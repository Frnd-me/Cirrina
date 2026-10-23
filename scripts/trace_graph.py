"""Render all observations and relationships in a GraphML execution trace."""

import argparse
import json
import subprocess
import sys
from collections.abc import Mapping
from pathlib import Path
from textwrap import fill
from typing import Any
from xml.etree.ElementTree import ParseError

import networkx as nx

INSTANCE_COLORS = (
    "#dbeafe",
    "#dcfce7",
    "#ffedd5",
    "#ede9fe",
    "#fce7f3",
    "#ccfbf1",
    "#fef9c3",
    "#e0e7ff",
)

EDGE_STYLES = {
    "event": {"color": "#2563a6", "penwidth": 1.5, "style": "solid"},
    "cause": {"color": "#667085", "style": "dashed"},
    "order": {"color": "#b0b5bd", "style": "solid"},
}


def instance_key(record: Mapping[str, Any]) -> tuple[str, str]:
    return (
        str(record.get("runtimeId", "")),
        str(record.get("instanceId") or record.get("instance", "")),
    )


def node_label(record: Mapping[str, Any]) -> str:
    """Keep identity, event and state visible without expanding every attribute."""
    event = record.get("event", record.get("topic", "?"))
    if record.get("missing") or record.get("type") == "UNOBSERVED":
        return f"Unobserved\n{event}" if event != "?" else "Unobserved record"

    machine = record.get("stateMachine", "unknown machine")
    instance = record.get("instance", "unknown instance")
    identity = str(record.get("instanceId", ""))[:8]
    heading = f"{machine} / {instance}"
    if identity:
        heading += f" [{identity}]"

    match record.get("type"):
        case "INSTANCE_CREATED":
            detail = "created"
        case "STATE_ENTERED":
            detail = f"enter {record.get('state', '?')}"
        case "EVENT_EMITTED":
            detail = f"send {event}\nstate: {record.get('state', '?')}"
        case "EVENT_HANDLED":
            before = record.get("stateBefore", "?")
            after = record.get("stateAfter", "?")
            state = before if before == after else f"{before} → {after}"
            detail = f"handle {event}\n{state}"
        case _:
            detail = str(record.get("type", "observation"))

    if record.get("error"):
        detail += "\nERROR"

    return "\n".join(fill(line, 40) for line in f"{heading}\n{detail}".splitlines())


def tooltip(attributes: Mapping[str, Any]) -> str:
    return "\n".join(f"{key}: {value}" for key, value in sorted(attributes.items()))


def dot_attributes(attributes: Mapping[str, Any]) -> str:
    return ", ".join(
        f"{key}={json.dumps(str(value), ensure_ascii=False)}"
        for key, value in attributes.items()
    )


def build_dot(graph: nx.MultiDiGraph) -> str:
    """Describe every node and parallel edge in styled DOT for Graphviz."""
    if not graph or not graph.is_directed():
        raise ValueError("the trace must be a nonempty directed graph")

    instances = sorted({instance_key(record) for _, record in graph.nodes(data=True)})
    colors = {
        instance: INSTANCE_COLORS[index % len(INSTANCE_COLORS)]
        for index, instance in enumerate(instances)
    }
    heading = (
        f"Execution trace · {len(graph)} observations · {graph.number_of_edges()} links\n"
        "Blue: event delivery · Dashed: cause · Gray: local order\n"
        "Node colors identify instances · Hover for full record details"
    )
    if graph.graph.get("truncated"):
        heading += "\nPartial export: some observations were omitted"

    lines = [
        "digraph trace {",
        "  graph ["
        + dot_attributes(
            {
                "label": heading,
                "labelloc": "t",
                "fontname": "sans-serif",
                "fontsize": 13,
                "rankdir": "TB",
                "nodesep": 0.35,
                "ranksep": 0.55,
                "pad": 0.3,
                "bgcolor": "white",
            }
        )
        + "];",
        (
            '  node [shape=box, style="rounded,filled", fontname="sans-serif", '
            'fontsize=10, margin="0.12,0.09", color="#9aa4b2", fontcolor="#1f2937"];'
        ),
        '  edge [arrowsize=0.6, fontname="sans-serif", fontsize=9, fontcolor="#2563a6"];',
    ]

    # Generated identifiers avoid interpreting trace IDs as Graphviz syntax.
    node_ids = {node: f"n{index}" for index, node in enumerate(graph)}
    for node, record in graph.nodes(data=True):
        missing = record.get("missing") or record.get("type") == "UNOBSERVED"
        attributes = {
            "label": node_label(record),
            "tooltip": tooltip({"recordId": node, **record}),
            "fillcolor": "#f3f4f6" if missing else colors[instance_key(record)],
        }
        if missing:
            attributes["style"] = "rounded,filled,dashed"
        if record.get("error"):
            attributes["color"] = "#b42318"
            attributes["penwidth"] = "2"
        lines.append(f"  {node_ids[node]} [{dot_attributes(attributes)}];")

    for source, target, key, edge in graph.edges(keys=True, data=True):
        kind = edge.get("kind", "unknown")
        attributes = {
            **EDGE_STYLES.get(kind, {"color": "#667085", "style": "dotted"}),
            "tooltip": tooltip({"edgeKey": key, **edge}),
        }
        if kind == "event":
            attributes["label"] = fill(str(edge.get("topic", "event")), 30)
        lines.append(
            f"  {node_ids[source]} -> {node_ids[target]} [{dot_attributes(attributes)}];"
        )

    lines.append("}")
    return "\n".join(lines)


def render_graph(graph: nx.MultiDiGraph, output: Path) -> None:
    source = build_dot(graph)
    output.parent.mkdir(parents=True, exist_ok=True)
    try:
        result = subprocess.run(
            ["dot", f"-T{output.suffix[1:].lower()}"],
            input=source.encode("utf-8"),
            capture_output=True,
            check=True,
        )
    except FileNotFoundError as error:
        raise ValueError(
            "Graphviz is required; install it and put 'dot' on PATH"
        ) from error
    except subprocess.CalledProcessError as error:
        raise ValueError(
            error.stderr.decode("utf-8", errors="replace").strip()
        ) from error
    output.write_bytes(result.stdout)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="GraphML file from trace_exporter.py")
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        help="Output .svg, .png or .pdf (default: INPUT.graph.svg)",
    )
    args = parser.parse_args(argv)
    output = args.output or args.input.with_suffix(".graph.svg")
    if output.suffix.lower() not in {".svg", ".png", ".pdf"}:
        parser.error("--output must end in .svg, .png or .pdf")
    if output.resolve() == args.input.resolve():
        parser.error("--output must differ from the input file")

    try:
        graph = nx.read_graphml(args.input, force_multigraph=True)
        render_graph(graph, output)
    except (OSError, ValueError, ParseError, nx.NetworkXException) as error:
        print(f"Cannot visualize trace: {error}", file=sys.stderr)
        return 1

    print(f"Rendered trace to {output.resolve()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
