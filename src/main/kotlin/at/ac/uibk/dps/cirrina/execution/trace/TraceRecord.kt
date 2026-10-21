package at.ac.uibk.dps.cirrina.execution.trace

import at.ac.uibk.dps.cirrina.util.getInsecureUuid

enum class TraceRecordType {
  INSTANCE_CREATED,
  STATE_ENTERED,
  EVENT_EMITTED,
  EVENT_HANDLED,
}

sealed class TraceRecord(id: String = getInsecureUuid().toString()) {
  val id: String = id

  abstract val type: TraceRecordType

  abstract val instance: String

  abstract val stateMachine: String

  /** The id of the record that causally produced this record, if any. */
  abstract val causalParent: String?

  data class InstanceCreated(
    override val instance: String,
    override val stateMachine: String,
    override val causalParent: String? = null,
    id: String = getInsecureUuid().toString(),
  ) : TraceRecord(id) {
    override val type = TraceRecordType.INSTANCE_CREATED
  }

  data class StateEntered(
    override val instance: String,
    override val stateMachine: String,
    val state: String,
    override val causalParent: String? = null,
    id: String = getInsecureUuid().toString(),
  ) : TraceRecord(id) {
    override val type = TraceRecordType.STATE_ENTERED
  }

  data class EventEmitted(
    override val instance: String,
    override val stateMachine: String,
    val event: String,
    val eventId: String,
    val channel: String,
    val target: String,
    override val causalParent: String? = null,
    id: String = getInsecureUuid().toString(),
  ) : TraceRecord(id) {
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
    override val causalParent: String? = null,
    id: String = getInsecureUuid().toString(),
  ) : TraceRecord(id) {
    override val type = TraceRecordType.EVENT_HANDLED
  }
}
