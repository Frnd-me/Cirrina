package at.ac.uibk.dps.cirrina.execution.trace

import java.util.concurrent.ConcurrentLinkedQueue

/** Captured when an action starts, so async completions keep their original cause and state. */
class TraceCause
private constructor(
  val id: String,
  val state: String?,
  private val emitted: ConcurrentLinkedQueue<String>?,
) {
  constructor(
    id: String,
    state: String?,
    collectEmissions: Boolean = false,
  ) : this(id, state, if (collectEmissions) ConcurrentLinkedQueue() else null)

  internal fun inState(state: String) = TraceCause(id, state, emitted)

  internal fun emitted(eventId: String) {
    emitted?.add(eventId)
  }

  internal fun emittedEventIds(): List<String> = emitted?.toList().orEmpty()
}
