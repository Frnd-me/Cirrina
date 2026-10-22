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
poetry run python scripts/trace_visualizer.py trace.graphml --output trace.svg
# or
poetry run python scripts/trace_exporter.py --output trace.dot
```

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