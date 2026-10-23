"""Render a GraphML execution trace as a Lamport-style event diagram."""

import argparse
import sys
from collections.abc import Mapping
from pathlib import Path
from textwrap import fill
from typing import Any
from xml.etree.ElementTree import ParseError

import networkx as nx
from matplotlib import rc_context
from matplotlib.axes import Axes
from matplotlib.figure import Figure
from matplotlib.patches import FancyArrowPatch

HIDDEN_RECORD_TYPES = {"INSTANCE_CREATED", "STATE_ENTERED"}


def prepare_graph(graph: nx.MultiDiGraph) -> nx.MultiDiGraph:
    if not graph or not graph.is_directed():
        raise ValueError("the trace must be a nonempty directed graph")
    if not nx.is_directed_acyclic_graph(graph):
        raise ValueError("the trace contains a cycle; causal time cannot be laid out")

    visible = graph.copy()

    for node in nx.topological_sort(graph):
        if graph.nodes[node].get("type") not in HIDDEN_RECORD_TYPES:
            continue

        incoming = list(visible.in_edges(node, data=True))
        outgoing = list(visible.out_edges(node, data=True))

        for source, _, before in incoming:
            for _, target, after in outgoing:
                kind = before.get("kind")

                if kind not in {"order", "cause"} or kind != after.get("kind"):
                    kind = "layout"

                visible.add_edge(source, target, key=kind, kind=kind)

        visible.remove_node(node)

    if not visible:
        raise ValueError("the trace has no events to visualize")

    return visible


def instance_key(record: Mapping[str, Any]) -> tuple[str, str]:
    if record.get("missing") or record.get("type") == "UNOBSERVED":
        return ("", "")

    return (
        str(record.get("runtimeId", "")),
        str(record.get("instanceId") or record.get("instance", "unknown")),
    )


def lane_label(record: Mapping[str, Any]) -> str:
    if instance_key(record) == ("", ""):
        return "Unobserved\nexternal input / omitted record"

    machine = record.get("stateMachine", "unknown machine")
    name = record.get("instance", "unknown instance")
    identity = str(record.get("instanceId", "unknown"))[:8]
    runtime = str(record.get("runtimeId", "unknown"))[:8]
    heading = fill(f"{machine} / {name}", 36)

    return f"{heading}\nid: {identity} · runtime: {runtime}"


def event_label(record: Mapping[str, Any]) -> str:
    event = record.get("event", record.get("topic", "?"))

    match record.get("type"):
        case "EVENT_EMITTED":
            label = f"send {event}\nfrom {record.get('state', '?')}"
        case "EVENT_HANDLED":
            before = record.get("stateBefore", "?")
            after = record.get("stateAfter", "?")
            state = before if before == after else f"{before} → {after}"
            label = f"handle {event}\n{state}"
            if record.get("error"):
                label += "\n(error)"
        case _:
            label = f"unobserved {event}" if event != "?" else "unobserved"

    return "\n".join(fill(line, 30) for line in label.splitlines())


def layout_trace(
    graph: nx.MultiDiGraph,
) -> tuple[dict[str, tuple[int, int]], list[str]]:
    labels_by_instance = {
        instance_key(record): lane_label(record) for _, record in graph.nodes(data=True)
    }
    instances = sorted(
        labels_by_instance,
        key=lambda instance: (labels_by_instance[instance], instance),
    )
    lane_numbers = {instance: index for index, instance in enumerate(instances)}
    labels = [labels_by_instance[instance] for instance in instances]

    order = nx.lexicographical_topological_sort(
        graph,
        key=lambda node: (graph.nodes[node].get("timestamp", 0), str(node)),
    )
    positions: dict[str, tuple[int, int]] = {}
    last_lane_row = [-1] * len(instances)

    for node in order:
        lane = lane_numbers[instance_key(graph.nodes[node])]
        predecessor_row = max(
            (positions[parent][1] for parent in graph.predecessors(node)), default=-1
        )
        row = max(predecessor_row, last_lane_row[lane]) + 1
        positions[node] = (lane, row)
        last_lane_row[lane] = row

    return positions, labels


def draw_edges(
    axes: Axes, graph: nx.MultiDiGraph, positions: Mapping[str, tuple[int, int]]
) -> None:
    for source, target, edge in graph.edges(data=True):
        start, end = positions[source], positions[target]
        kind = edge.get("kind")

        if kind == "order":
            color, linestyle, curvature = "0.6", "-", 0
        elif kind == "event":
            color, linestyle = "#356887", "-"
            curvature = 0.4 if start[0] == end[0] else 0
        elif kind == "cause" and start[0] != end[0]:
            color, linestyle, curvature = "0.5", "--", 0
        else:
            continue

        axes.add_patch(
            FancyArrowPatch(
                start,
                end,
                arrowstyle="-|>",
                mutation_scale=10,
                connectionstyle=f"arc3,rad={curvature}",
                color=color,
                linestyle=linestyle,
                linewidth=1,
                shrinkA=4,
                shrinkB=4,
                zorder=1,
            )
        )


def draw_events(
    axes: Axes, graph: nx.MultiDiGraph, positions: Mapping[str, tuple[int, int]]
) -> None:
    for node, (lane, row) in positions.items():
        axes.plot(lane, row, "o", markersize=4, color="0.25", zorder=3)
        axes.text(
            lane + 0.04,
            row,
            event_label(graph.nodes[node]),
            fontsize=8,
            va="center",
            bbox={"facecolor": "white", "edgecolor": "none", "pad": 1},
            zorder=2,
        )


def draw_trace(graph: nx.MultiDiGraph) -> Figure:
    graph = prepare_graph(graph)
    positions, labels = layout_trace(graph)
    last_row = max(row for _, row in positions.values())

    width = max(7, len(labels) * 4)
    height = max(5, (last_row + 5) * 0.5)

    figure = Figure(figsize=(width, height))
    axes = figure.subplots()

    figure.subplots_adjust(left=0.05, right=0.97, top=0.91, bottom=0.04)

    for lane, label in enumerate(labels):
        axes.axvline(lane, color="0.85", linewidth=1, linestyle=":", zorder=0)
        axes.text(
            lane + 0.25,
            1.02,
            label,
            transform=axes.get_xaxis_transform(),
            ha="center",
            va="bottom",
            fontsize=9,
        )

    draw_edges(axes, graph, positions)
    draw_events(axes, graph, positions)

    axes.set_xlim(-0.35, len(labels) - 0.1)
    axes.set_ylim(last_row + 1, -1)
    axes.set_axis_off()

    return figure


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)

    parser.add_argument("input", type=Path, help="GraphML file from trace_exporter.py")
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        help="Output .svg, .png or .pdf (default: INPUT.svg)",
    )

    args = parser.parse_args(argv)

    output = args.output or args.input.with_suffix(".svg")
    if output.suffix.lower() not in {".svg", ".png", ".pdf"}:
        parser.error("--output must end in .svg, .png or .pdf")

    try:
        graph = nx.read_graphml(args.input, force_multigraph=True)
        with rc_context(
            {"svg.fonttype": "none", "text.usetex": False, "text.parse_math": False}
        ):
            figure = draw_trace(graph)

            output.parent.mkdir(parents=True, exist_ok=True)

            figure.savefig(output, dpi=150, bbox_inches="tight")
    except (OSError, ValueError, ParseError, nx.NetworkXException) as error:
        print(f"Cannot visualize trace: {error}", file=sys.stderr)

        return 1

    print(f"Rendered trace to {output.resolve()}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
