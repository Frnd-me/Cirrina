package at.ac.uibk.dps.cirrina.execution.trace

interface TraceRecorder : AutoCloseable {
  val enabled: Boolean
    get() = true

  fun record(record: TraceRecord)

  companion object {
    val noop =
      object : TraceRecorder {
        override val enabled = false

        override fun record(record: TraceRecord) {}

        override fun close() {}
      }
  }
}
