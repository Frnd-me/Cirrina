package at.ac.uibk.dps.cirrina.execution.trace

import com.google.gson.Gson
import com.google.gson.JsonParser

/** JSON-lines codec used by the durable trace spool. */
object Serializer {
  private val gson = Gson()

  fun serialize(record: TraceRecord): String = gson.toJson(record)

  fun deserialize(line: String): TraceRecord {
    val json = JsonParser.parseString(line).asJsonObject
    val type =
      when (TraceRecordType.valueOf(json.get("type").asString)) {
        TraceRecordType.INSTANCE_CREATED -> TraceRecord.InstanceCreated::class.java
        TraceRecordType.STATE_ENTERED -> TraceRecord.StateEntered::class.java
        TraceRecordType.EVENT_EMITTED -> TraceRecord.EventEmitted::class.java
        TraceRecordType.EVENT_HANDLED -> TraceRecord.EventHandled::class.java
      }
    return gson.fromJson(json, type)
  }
}
