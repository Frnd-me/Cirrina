package at.ac.uibk.dps.cirrina.execution.trace

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TraceRecordTest {
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
      assertEquals(record, Serializer.deserialize(line))
    }
  }
}
