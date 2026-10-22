package at.ac.uibk.dps.cirrina

enum class PersistentContextProvider {
  ETCD
}

data class EnvironmentVariable<T>(
  val name: String,
  val default: T,
  val mapper: (String) -> T = { it as T },
) {
  fun get(): T {
    val value = System.getenv(name)
    return when {
      value != null -> mapper(value)
      else -> default
    }
  }
}

object EnvironmentVariables {
  val etcdContextUrl = EnvironmentVariable<String?>("ETCD_CONTEXT_URL", null)

  val zenohEventHandlerConfigUri =
    EnvironmentVariable<String?>("ZENOH_EVENT_HANDLER_CONFIG_URI", null)

  val metricsDirectory = EnvironmentVariable("METRICS_DIRECTORY", "metrics")
  val metricsPeriod = EnvironmentVariable("METRICS_PERIOD", 1L)

  val traceRecorder = EnvironmentVariable<String?>("TRACE_RECORDER", null)
  val traceSpoolDirectory = EnvironmentVariable("TRACE_SPOOL_DIRECTORY", "trace-spool")

  val neo4jUri = EnvironmentVariable("NEO4J_URI", "bolt://localhost:7687")
  val neo4jDatabase = EnvironmentVariable("NEO4J_DATABASE", "neo4j")
  val neo4jUser = EnvironmentVariable("NEO4J_USER", "neo4j")
  val neo4jPassword = EnvironmentVariable("NEO4J_PASSWORD", "password")

  val mainUri = EnvironmentVariable("MAIN_URI", "file:///app/main.pkl")

  val run =
    EnvironmentVariable(
      "RUN",
      emptyList(),
      { value -> value.split(",").filter { it.isNotBlank() } },
    )

  val contextProvider =
    EnvironmentVariable(
      "CONTEXT_PROVIDER",
      PersistentContextProvider.ETCD,
      { value ->
        try {
          PersistentContextProvider.valueOf(value.uppercase())
        } catch (_: Exception) {
          error("invalid value for environment variable CONTEXT_PROVIDER")
        }
      },
    )
}
