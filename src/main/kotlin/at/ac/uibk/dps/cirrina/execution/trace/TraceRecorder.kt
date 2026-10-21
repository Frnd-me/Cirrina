package at.ac.uibk.dps.cirrina.execution.trace

interface TraceRecorder : AutoCloseable {
  fun record(record: TraceRecord)

  companion object {
    val noop = object : TraceRecorder {
      override fun record(record: TraceRecord) {}

      override fun close() {}
    }
  }
}
