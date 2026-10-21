package at.ac.uibk.dps.cirrina.execution.trace

object Serializer {
  fun serialize(record: TraceRecord): String = serializeTraceRecord(record)

  fun deserialize(line: String): TraceRecord = deserializeTraceRecord(line)
}
