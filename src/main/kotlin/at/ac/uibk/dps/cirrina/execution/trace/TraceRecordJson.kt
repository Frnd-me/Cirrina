package at.ac.uibk.dps.cirrina.execution.trace

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer

private val gson =
  GsonBuilder().registerTypeAdapter(TraceRecord::class.java, TraceRecordAdapter()).create()

private class TraceRecordAdapter : JsonSerializer<TraceRecord>, JsonDeserializer<TraceRecord> {
  override fun serialize(record: TraceRecord, type: java.lang.reflect.Type, context: JsonSerializationContext): JsonElement {
    val json = JsonObject()
    json.addProperty("recordType", record.type.name)
    json.addProperty("id", record.id)
    json.addProperty("instance", record.instance)
    json.addProperty("stateMachine", record.stateMachine)
    json.add("causalParent", context.serialize(record.causalParent))

    when (record) {
      is TraceRecord.InstanceCreated -> {}
      is TraceRecord.StateEntered -> json.addProperty("state", record.state)
      is TraceRecord.EventEmitted -> {
        json.addProperty("event", record.event)
        json.addProperty("eventId", record.eventId)
        json.addProperty("channel", record.channel)
        json.addProperty("target", record.target)
      }
      is TraceRecord.EventHandled -> {
        json.addProperty("event", record.event)
        json.addProperty("eventId", record.eventId)
        json.addProperty("channel", record.channel)
        json.add("stateBefore", context.serialize(record.stateBefore))
        json.add("stateAfter", context.serialize(record.stateAfter))
        json.add("emittedEventIds", context.serialize(record.emittedEventIds))
      }
    }

    return json
  }

  override fun deserialize(json: JsonElement, type: java.lang.reflect.Type, context: JsonDeserializationContext): TraceRecord {
    val obj = json.asJsonObject
    val id = obj.get("id").asString
    val recordType = TraceRecordType.valueOf(obj.get("recordType").asString)
    val instance = obj.get("instance").asString
    val stateMachine = obj.get("stateMachine").asString
    val causalParent = obj.get("causalParent")?.takeUnless { it.isJsonNull }?.asString

    return when (recordType) {
      TraceRecordType.INSTANCE_CREATED ->
        TraceRecord.InstanceCreated(instance, stateMachine, causalParent, id)
      TraceRecordType.STATE_ENTERED ->
        TraceRecord.StateEntered(
          instance,
          stateMachine,
          obj.get("state").asString,
          causalParent,
          id,
        )
      TraceRecordType.EVENT_EMITTED ->
        TraceRecord.EventEmitted(
          instance,
          stateMachine,
          obj.get("event").asString,
          obj.get("eventId").asString,
          obj.get("channel").asString,
          obj.get("target").asString,
          causalParent,
          id,
        )
      TraceRecordType.EVENT_HANDLED ->
        TraceRecord.EventHandled(
          instance,
          stateMachine,
          obj.get("event").asString,
          obj.get("eventId").asString,
          obj.get("channel").asString,
          obj.get("stateBefore")?.takeUnless { it.isJsonNull }?.asString,
          obj.get("stateAfter")?.takeUnless { it.isJsonNull }?.asString,
          context.deserialize(obj.get("emittedEventIds"), List::class.java) as List<String>,
          causalParent,
          id,
        )
    }
  }
}

private fun serializeTraceRecord(record: TraceRecord): String = gson.toJson(record)

private fun deserializeTraceRecord(line: String): TraceRecord =
  gson.fromJson(line, TraceRecord::class.java)
