import argparse
import json
import os
import sys
from collections import defaultdict
from collections.abc import Iterable, Mapping
from pathlib import Path
from typing import Any

import networkx as nx
from neo4j import Driver, GraphDatabase, Record
from neo4j.exceptions import DriverError, Neo4jError


def query(
    driver: Driver, database: str, cypher: str, **parameters: Any
) -> list[Record]:
    return driver.execute_query(
        cypher, parameters_=parameters, database_=database, routing_="r"
    ).records


def latest_execution(driver: Driver, database: str) -> list[str]:
    latest = query(
        driver,
        database,
        """
        MATCH (record:TraceRecord) WHERE record.runtimeId IS NOT NULL
        RETURN record.runtimeId AS id
        ORDER BY record.timestamp DESC, record.id LIMIT 1
        """,
    )
    if not latest:
        return []

    runtimes = {latest[0]["id"]}

    frontier = runtimes.copy()
    while frontier:
        peers = query(
            driver,
            database,
            """
            MATCH (sender:EventEmitted)-[:EMITS]->(:TraceEvent)
                  <-[:HANDLES]-(receiver:EventHandled)
            WHERE sender.runtimeId IN $ids OR receiver.runtimeId IN $ids
            RETURN DISTINCT sender.runtimeId AS sender, receiver.runtimeId AS receiver
            """,
            ids=sorted(frontier),
        )
        connected = {
            row[key]
            for row in peers
            for key in ("sender", "receiver")
            if row[key] is not None
        }
        frontier = connected - runtimes

        runtimes.update(frontier)

    return sorted(runtimes)


def add_boundary(graph: nx.MultiDiGraph, node_id: str, **attributes: Any) -> None:
    if node_id not in graph:
        graph.add_node(node_id, type="UNOBSERVED", missing=True, **attributes)


def build_graph(records: Iterable[Mapping[str, Any]]) -> nx.MultiDiGraph:
    records = list(records)
    graph = nx.MultiDiGraph()
    emissions: dict[str, list[str]] = defaultdict(list)

    for record in records:
        attributes = {
            name: json.dumps(value) if isinstance(value, (list, dict)) else value
            for name, value in record.items()
            if value is not None
        }
        graph.add_node(record["id"], **attributes, missing=False)
        if record.get("type") == "EVENT_EMITTED":
            emissions[record["eventId"]].append(record["id"])

    for record in records:
        target = record["id"]
        for field, kind in (("causalParent", "cause"), ("previousRecord", "order")):
            source = record.get(field)
            if source:
                add_boundary(graph, source)
                graph.add_edge(source, target, key=kind, kind=kind)

        if record.get("type") != "EVENT_HANDLED":
            continue

        event_id = record["eventId"]
        event = {
            "eventId": event_id,
            "topic": record.get("event", ""),
            "channel": record.get("channel", ""),
        }
        senders = emissions.get(event_id, [])
        if not senders:
            source = f"event:{event_id}"
            add_boundary(graph, source, **event)
            senders = [source]
        for source in senders:
            graph.add_edge(
                source, target, key=f"event:{event_id}", kind="event", **event
            )

    return graph


def load_trace_graph(
    driver: Driver,
    database: str = "neo4j",
    runtimes: list[str] | None = None,
    limit: int = 200,
) -> nx.MultiDiGraph:
    if limit < 1:
        raise ValueError("limit must be positive")
    if runtimes is None:
        runtimes = latest_execution(driver, database)

    rows = query(
        driver,
        database,
        """
        MATCH (record:TraceRecord) WHERE record.runtimeId IN $runtimes
        RETURN properties(record) AS record
        ORDER BY record.timestamp DESC, record.id LIMIT $limit
        """,
        runtimes=runtimes,
        limit=limit + 1,
    )

    graph = build_graph(row["record"] for row in rows[:limit])

    graph.graph.update(truncated=len(rows) > limit, runtimeIds=json.dumps(runtimes))

    return graph


def dot_attributes(attributes: Mapping[str, Any]) -> str:
    return ", ".join(
        f"{json.dumps(str(name), ensure_ascii=False)}="
        f"{json.dumps(str(value), ensure_ascii=False)}"
        for name, value in attributes.items()
    )


def write_dot(graph: nx.MultiDiGraph, path: Path) -> None:
    with path.open("w", encoding="utf-8") as output:
        output.write("digraph trace {\n")

        if graph.graph:
            output.write(f"  graph [{dot_attributes(graph.graph)}];\n")

        for node_id, attributes in graph.nodes(data=True):
            node = json.dumps(str(node_id), ensure_ascii=False)
            output.write(f"  {node} [{dot_attributes(attributes)}];\n")

        for source, target, key, attributes in graph.edges(keys=True, data=True):
            source_id = json.dumps(str(source), ensure_ascii=False)
            target_id = json.dumps(str(target), ensure_ascii=False)
            edge = dot_attributes({"key": key, **attributes})
            output.write(f"  {source_id} -> {target_id} [{edge}];\n")

        output.write("}\n")


def export_graph(graph: nx.MultiDiGraph, path: Path) -> None:
    writers = {".dot": write_dot, ".graphml": nx.write_graphml}
    writer = writers.get(path.suffix.lower())

    if writer is None:
        raise ValueError("output must end in .dot or .graphml")

    path.parent.mkdir(parents=True, exist_ok=True)

    writer(graph, path)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)

    parser.add_argument(
        "--uri", default=os.getenv("NEO4J_URI", "bolt://localhost:7687")
    )
    parser.add_argument("--user", default=os.getenv("NEO4J_USER", "neo4j"))
    parser.add_argument("--password", default=os.getenv("NEO4J_PASSWORD", "password"))
    parser.add_argument("--database", default=os.getenv("NEO4J_DATABASE", "neo4j"))
    parser.add_argument(
        "--runtime",
        action="append",
        help="Runtime ID to include; repeat for multiple runtimes",
    )
    parser.add_argument(
        "--list-runtimes",
        action="store_true",
        help="List recent runtime IDs and instance names",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=200,
        help="Maximum observations to export (default: 200)",
    )
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        default=Path("trace.dot"),
        help="Output .dot or .graphml file",
    )

    args = parser.parse_args(argv)

    if args.limit < 1:
        parser.error("--limit must be positive")
    if args.output.suffix.lower() not in {".dot", ".graphml"}:
        parser.error("--output must end in .dot or .graphml")

    return args


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)

    try:
        with GraphDatabase.driver(
            args.uri,
            auth=(args.user, args.password),
            connection_timeout=5,
            connection_acquisition_timeout=10,
            max_transaction_retry_time=5,
        ) as driver:
            if args.list_runtimes:
                rows = query(
                    driver,
                    args.database,
                    """
                    MATCH (record:TraceRecord) WHERE record.runtimeId IS NOT NULL
                    RETURN record.runtimeId AS id, collect(DISTINCT record.instance) AS instances,
                           max(record.timestamp) AS lastSeen
                    ORDER BY lastSeen DESC LIMIT 20
                    """,
                )

                for row in rows:
                    print(row["id"], ", ".join(sorted(row["instances"])), sep="  ")

                if not rows:
                    print("No trace runtimes found.")

                return 0

            graph = load_trace_graph(driver, args.database, args.runtime, args.limit)

        if not graph:
            raise ValueError("No trace records found for the selected execution.")

        export_graph(graph, args.output)
    except (Neo4jError, DriverError, OSError, ValueError) as error:
        print(f"Cannot export trace: {error}", file=sys.stderr)
        return 1

    print(
        f"Exported {graph.number_of_nodes()} nodes / {graph.number_of_edges()} edges "
        f"to {args.output.resolve()}"
    )
    print("Runtimes: " + ", ".join(json.loads(graph.graph["runtimeIds"])))

    if graph.graph["truncated"]:
        print(
            "Partial export: increase --limit to include earlier observations.",
            file=sys.stderr,
        )

    return 0


if __name__ == "__main__":
    sys.exit(main())
