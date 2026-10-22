package at.ac.uibk.dps.cirrina.execution.trace

import at.ac.uibk.dps.cirrina.util.getInsecureUuid

enum class TraceRecordType {
  INSTANCE_CREATED,
  STATE_ENTERED,
  EVENT_EMITTED,
  EVENT_HANDLED,
}

sealed class TraceRecord {
  abstract val id: String
  abstract val instanceId: String
  abstract val runtimeId: String
  abstract val timestamp: Long
  abstract val previousRecord: String?
  abstract val type: TraceRecordType
  abstract val instance: String
  abstract val stateMachine: String
  abstract val causalParent: String?

  data class InstanceCreated(
    override val instance: String,
    override val stateMachine: String,
    override val causalParent: String? = null,
    override val id: String = getInsecureUuid().toString(),
    override val instanceId: String = instance,
    override val runtimeId: String = "",
    override val timestamp: Long = System.currentTimeMillis(),
    override val previousRecord: String? = null,
  ) : TraceRecord() {
    override val type = TraceRecordType.INSTANCE_CREATED
  }

  data class StateEntered(
    override val instance: String,
    override val stateMachine: String,
    val state: String,
    override val causalParent: String? = null,
    override val id: String = getInsecureUuid().toString(),
    override val instanceId: String = instance,
    override val runtimeId: String = "",
    override val timestamp: Long = System.currentTimeMillis(),
    override val previousRecord: String? = null,
  ) : TraceRecord() {
    override val type = TraceRecordType.STATE_ENTERED
  }

  data class EventEmitted(
    override val instance: String,
    override val stateMachine: String,
    val event: String,
    val eventId: String,
    val channel: String,
    val target: String,
    val state: String? = null,
    override val causalParent: String? = null,
    override val id: String = getInsecureUuid().toString(),
    override val instanceId: String = instance,
    override val runtimeId: String = "",
    override val timestamp: Long = System.currentTimeMillis(),
    override val previousRecord: String? = null,
  ) : TraceRecord() {
    override val type = TraceRecordType.EVENT_EMITTED
  }

  data class EventHandled(
    override val instance: String,
    override val stateMachine: String,
    val event: String,
    val eventId: String,
    val channel: String,
    val stateBefore: String?,
    val stateAfter: String?,
    val emittedEventIds: List<String>,
    val source: String = "",
    val target: String = "",
    val error: String? = null,
    override val causalParent: String? = null,
    override val id: String = getInsecureUuid().toString(),
    override val instanceId: String = instance,
    override val runtimeId: String = "",
    override val timestamp: Long = System.currentTimeMillis(),
    override val previousRecord: String? = null,
  ) : TraceRecord() {
    override val type = TraceRecordType.EVENT_HANDLED
  }
}

internal fun TraceRecord.properties(): Map<String, Any?> =
  linkedMapOf<String, Any?>(
      "id" to id,
      "type" to type.name,
      "instance" to instance,
      "instanceId" to instanceId,
      "runtimeId" to runtimeId,
      "stateMachine" to stateMachine,
      "timestamp" to timestamp,
      "causalParent" to causalParent,
      "previousRecord" to previousRecord,
    )
    .apply {
      when (val record = this@properties) {
        is TraceRecord.InstanceCreated -> Unit
        is TraceRecord.StateEntered -> put("state", record.state)
        is TraceRecord.EventEmitted -> {
          put("event", record.event)
          put("eventId", record.eventId)
          put("channel", record.channel)
          put("target", record.target)
          put("state", record.state)
        }
        is TraceRecord.EventHandled -> {
          put("event", record.event)
          put("eventId", record.eventId)
          put("channel", record.channel)
          put("stateBefore", record.stateBefore)
          put("stateAfter", record.stateAfter)
          put("emittedEventIds", record.emittedEventIds)
          put("source", record.source)
          put("target", record.target)
          put("error", record.error)
        }
      }
    }
