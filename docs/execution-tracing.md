# Execution tracing

```sh
docker compose up -d --wait neo4j
export TRACE_RECORDER=NEO4J
export NEO4J_URI=bolt://localhost:7687
export NEO4J_USER=neo4j
export NEO4J_PASSWORD=password
```

Run the tracing example:

```sh
MAIN_URI="file://$PWD/examples/tracing/fanOut.pkl" \
RUN=sender,workerA,workerB \
./gradlew run
```

Records are queued in memory and written in the background. Failed writes or a full queue drop
records. Neo4j Browser: http://localhost:7474.

Export the latest execution and draw its event sequence:

```sh
poetry install --no-root
poetry run python scripts/trace_exporter.py --output trace.graphml
poetry run python scripts/trace_lamport.py trace.graphml --output trace.lamport.svg
poetry run python scripts/trace_graph.py trace.graphml --output trace.graph.svg
poetry run python scripts/trace_timeline.py trace.graphml --output trace.timeline.html
# or
poetry run python scripts/trace_exporter.py --output trace.dot
```

The graph view requires Graphviz (`dot` on PATH). Open the SVG in a browser to zoom in.
Open `trace.timeline.html` in a browser and select X and Y to measure their recorded time difference
in milliseconds. Cross-machine comparisons assume synchronized clocks.

Choose another execution or export more records:

```sh
poetry run python scripts/trace_exporter.py --list-runtimes
poetry run python scripts/trace_exporter.py --runtime RUNTIME_ID --limit 1000 --output trace.graphml
```

Load the GraphML in NetworkX:

```python
import networkx as nx

graph = nx.read_graphml("trace.graphml", force_multigraph=True)
longest_path = nx.dag_longest_path(graph)
```
