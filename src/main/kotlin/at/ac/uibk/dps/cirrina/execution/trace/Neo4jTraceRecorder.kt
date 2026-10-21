package at.ac.uibk.dps.cirrina.execution.trace

import at.ac.uibk.dps.cirrina.EnvironmentVariables
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.appendText
import mu.KotlinLogging
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

private val logger = KotlinLogging.logger {}

class Neo4jTraceRecorder(private val driver: Driver, spoolDirectory: Path) : TraceRecorder {
  private val spool = Files.createDirectories(spoolDirectory)
  private val spoolFile = spool.resolve("pending.jsonl")

  private val executor =
    Executors.newSingleThreadExecutor { runnable ->
      Thread(runnable, "neo4j-trace-recorder").apply { isDaemon = true }
    }

  init {
    executor.submit { writeAll(readSpooled()) }
  }

  override fun record(record: TraceRecord) {
    val line = Serializer.serialize(record)

    try {
      executor.submit { writeAll(listOf(line)) }
    } catch (_: Exception) {
      appendToSpool(line)
    }
  }

  private fun writeAll(lines: List<String>) {
    if (lines.isEmpty()) return

    runCatching {
        driver.session().use { session ->
          session.executeWrite { tx ->
            tx.run(
                """
                UNWIND ${'$'}records AS record
                MERGE (i:TraceInstance {name: record.instance})
                MERGE (s:TraceStateMachine {name: record.stateMachine})
                MERGE (i)-[:OF]->(s)
                WITH record
                CALL (record) {
                  WITH record
                  MATCH (i:TraceInstance {name: record.instance})
                  CREATE (n:TraceRecord:InstanceCreated {
                    id: record.id, type: record.type, instance: record.instance,
                    stateMachine: record.stateMachine, causalParent: record.causalParent
                  })
                  RETURN n
                  UNION ALL
                  WITH record
                  MATCH (i:TraceInstance {name: record.instance})
                  CREATE (n:TraceRecord:StateEntered {
                    id: record.id, type: record.type, instance: record.instance,
                    stateMachine: record.stateMachine, causalParent: record.causalParent,
                    state: record.state
                  })
                  RETURN n
                  UNION ALL
                  WITH record
                  MATCH (i:TraceInstance {name: record.instance})
                  MERGE (e:TraceEvent {id: record.eventId})
                  ON CREATE SET e.topic = record.event, e.channel = record.channel, e.target = record.target
                  CREATE (n:TraceRecord:EventEmitted {
                    id: record.id, type: record.type, instance: record.instance,
                    stateMachine: record.stateMachine, causalParent: record.causalParent
                  })
                  CREATE (n)-[:EMITS]->(e)
                  RETURN n
                  UNION ALL
                  WITH record
                  MATCH (i:TraceInstance {name: record.instance})
                  MERGE (e:TraceEvent {id: record.eventId})
                  ON CREATE SET e.topic = record.event, e.channel = record.channel
                  CREATE (n:TraceRecord:EventHandled {
                    id: record.id, type: record.type, instance: record.instance,
                    stateMachine: record.stateMachine, causalParent: record.causalParent,
                    stateBefore: record.stateBefore, stateAfter: record.stateAfter
                  })
                  CREATE (n)-[:HANDLES]->(e)
                  RETURN n
                }
                WITH record
                OPTIONAL MATCH (parent:TraceRecord {id: record.causalParent})
                WITH record, parent
                MATCH (n:TraceRecord {id: record.id})
                FOREACH (_ IN CASE WHEN parent IS NOT NULL THEN [1] ELSE [] END |
                  CREATE (n)-[:CAUSED_BY]->(parent)
                )
                """.trimIndent(),
                mapOf("records" to lines.map(Serializer::deserialize)),
              )
              .consume()
          }
        }

        Files.deleteIfExists(spoolFile)
      }
      .onFailure { exception ->
        logger.warn(exception) { "failed to write ${lines.size} trace records to neo4j" }
        lines.forEach(::appendToSpool)
      }
  }

  private fun readSpooled(): List<String> =
    runCatching { if (Files.exists(spoolFile)) Files.readAllLines(spoolFile) else emptyList() }
      .getOrDefault(emptyList())

  private fun appendToSpool(line: String) =
    runCatching { synchronized(spoolFile) { spoolFile.appendText("$line\n") } }
      .onFailure { exception -> logger.error(exception) { "failed to spool trace record" } }

  override fun close() {
    executor.shutdown()
    executor.awaitTermination(10, TimeUnit.SECONDS)
    driver.close()
  }

  companion object {
    fun create(): Neo4jTraceRecorder {
      val driver =
        GraphDatabase.driver(
          EnvironmentVariables.neo4jUri.get(),
          AuthTokens.basic(
            EnvironmentVariables.neo4jUser.get(),
            EnvironmentVariables.neo4jPassword.get(),
          ),
        )

      return Neo4jTraceRecorder(driver, Path.of(EnvironmentVariables.traceSpoolDirectory.get()))
    }
  }
}
