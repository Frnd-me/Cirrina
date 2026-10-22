package at.ac.uibk.dps.cirrina.execution.trace

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TraceRecordTest {
  @Test
  fun `copying a handling record preserves its causal identity`() {
    val handling =
      TraceRecord.EventHandled("i", "m", "e", "event-id", "INTERNAL", "a", null, emptyList())
    val completed = handling.copy(stateAfter = "b", emittedEventIds = listOf("next-event"))

    assertEquals(handling.id, completed.id)
    assertEquals(handling.timestamp, completed.timestamp)
  }

  @Test
  fun testSerializationRoundTrip() {
    val records =
      listOf(
        TraceRecord.InstanceCreated("instance1", "stateMachine1", causalParent = "parent1"),
        TraceRecord.StateEntered("instance1", "stateMachine1", "a", causalParent = "parent1"),
        TraceRecord.EventEmitted(
          "instance1",
          "stateMachine1",
          "e1",
          "event1",
          "external",
          "target1",
        ),
        TraceRecord.EventHandled(
          "instance1",
          "stateMachine1",
          "e1",
          "event1",
          "external",
          "a",
          "b",
          listOf("event2", "event3"),
        ),
      )

    records.forEach { record ->
      val line = Serializer.serialize(record)
      val restored = Serializer.deserialize(line)
      assertEquals(record, restored)
      assertEquals(record.id, restored.id)
      assertEquals(record.timestamp, restored.timestamp)
      assertEquals(record.properties(), restored.properties())
    }
  }
}
