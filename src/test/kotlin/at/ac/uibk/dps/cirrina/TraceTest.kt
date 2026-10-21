package at.ac.uibk.dps.cirrina

import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.di.DaggerTestComponent
import at.ac.uibk.dps.cirrina.di.TestModule
import at.ac.uibk.dps.cirrina.execution.provider.ContextInMemory
import at.ac.uibk.dps.cirrina.execution.trace.TraceRecorder
import at.ac.uibk.dps.cirrina.execution.trace.TraceRecord
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.measureTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertTimeout

class TraceTest {
  private class CollectingTraceRecorder : TraceRecorder {
    val records = ConcurrentLinkedQueue<TraceRecord>()

    override fun record(record: TraceRecord) {
      records.add(record)
    }

    override fun close() {}
  }

  @Test
  fun testTraceRecording() {
    assertTimeout(Duration.ofSeconds(10)) {
      assertDoesNotThrow {
        val context = ContextInMemory()
        val recorder = CollectingTraceRecorder()

        val runtime =
          DaggerTestComponent.builder()
            .testModule(
              TestModule(context, DefaultDescriptions.pingPong, listOf("ping", "pong"), recorder)
            )
            .build()
            .runtime()

        val duration = measureTime { runtime.run() }
        println("traced execution: $duration")

        assertEquals(100_000, context.get("v"))

        val created = recorder.records.filterIsInstance<TraceRecord.InstanceCreated>()
        assertEquals(setOf("ping", "pong"), created.map { it.instance }.toSet())

        val entered = recorder.records.filterIsInstance<TraceRecord.StateEntered>()
        assertTrue(entered.any { it.instance == "ping" && it.state == "a" })
        assertTrue(entered.any { it.instance == "ping" && it.state == "c" })

        val handled = recorder.records.filterIsInstance<TraceRecord.EventHandled>()
        assertTrue(handled.isNotEmpty())
        assertTrue(handled.all { it.eventId.isNotBlank() })

        val emitted = recorder.records.filterIsInstance<TraceRecord.EventEmitted>()
        assertTrue(emitted.isNotEmpty())

        val emittedEventIds = emitted.map { it.eventId }.toSet()
        assertTrue(handled.all { it.eventId in emittedEventIds })

        assertTrue(handled.any { it.emittedEventIds.isNotEmpty() })
        handled
          .filter { it.emittedEventIds.isNotEmpty() }
          .forEach { record ->
            assertTrue(record.emittedEventIds.all { it in emittedEventIds })
          }

        val recordIds = recorder.records.map { it.id }.toSet()
        recorder.records
          .mapNotNull { it.causalParent }
          .forEach { assertTrue(it in recordIds) }

        val causalChildren =
          recorder.records.groupBy({ it.causalParent }, { it.id }).filterKeys { it != null }
        val emittedByParent = emitted.groupBy { it.causalParent }
        causalChildren.forEach { (parent, children) ->
          val parentEmitted = emittedByParent[parent].orEmpty().map { it.eventId }.toSet()
          val handledByParent =
            handled.filter { it.id in children }.flatMap { it.emittedEventIds }.toSet()
          assertTrue(handledByParent.all { it in parentEmitted })
        }
      }
    }
  }
}
