package at.ac.uibk.dps.cirrina.execution.trace.neo4j

import at.ac.uibk.dps.cirrina.EnvironmentVariables
import at.ac.uibk.dps.cirrina.execution.trace.Serializer
import at.ac.uibk.dps.cirrina.execution.trace.TraceRecord
import at.ac.uibk.dps.cirrina.execution.trace.TraceRecorder
import at.ac.uibk.dps.cirrina.execution.trace.properties
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import mu.KotlinLogging
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Config
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.neo4j.driver.SessionConfig
import org.neo4j.driver.TransactionConfig

private val logger = KotlinLogging.logger {}

class Neo4jTraceRecorder(
  private val driver: Driver,
  spoolDirectory: Path,
  private val database: String = "neo4j",
) : TraceRecorder {
  private val spool = Files.createDirectories(spoolDirectory)

  private val lockChannel =
    FileChannel.open(
      spool.resolve("recorder.lock"),
      StandardOpenOption.CREATE,
      StandardOpenOption.WRITE,
    )

  private val directoryLock =
    try {
      checkNotNull(lockChannel.tryLock()) { "trace spool directory is already in use: $spool" }
    } catch (exception: Exception) {
      lockChannel.close()
      driver.close()
      throw exception
    }

  private val activeFile = spool.resolve("active.jsonl")

  private val appendLock = Any()
  private val drainLock = Any()

  private var closed = false
  private var schemaReady = false

  private val executor =
    Executors.newSingleThreadScheduledExecutor { runnable ->
      Thread(runnable, "neo4j-trace-recorder").apply { isDaemon = true }
    }

  init {
    executor.scheduleWithFixedDelay({ flush() }, 100, 100, TimeUnit.MILLISECONDS)
  }

  override fun record(record: TraceRecord) {
    val line = Serializer.serialize(record)

    synchronized(appendLock) {
      check(!closed) { "trace recorder is closed" }

      Files.writeString(activeFile, "$line\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }
  }

  fun flush(): Boolean =
    synchronized(drainLock) {
      runCatching {
          val pending =
            Files.list(spool).use { paths ->
              paths
                .filter { it.fileName.toString().endsWith(".jsonl") && it != activeFile }
                .sorted()
                .toList()
            }
          pending.forEach(::writeFile)

          val rotated =
            synchronized(appendLock) {
              if (Files.exists(activeFile) && Files.size(activeFile) > 0) {
                Files.move(activeFile, spool.resolve("pending-${UUID.randomUUID()}.jsonl"))
              } else null
            }
          rotated?.let(::writeFile)
        }
        .onFailure { exception ->
          logger.warn {
            "trace delivery failed; journal retained in '$spool': ${exception.message}"
          }
        }
        .isSuccess
    }

  private fun writeFile(file: Path) {
    driver.session(SessionConfig.forDatabase(database)).use { session ->
      if (!schemaReady) {
        listOf("TraceRecord", "TraceEvent", "TraceInstance", "TraceRuntime").forEach { label ->
          session
            .run(
              "CREATE CONSTRAINT cirrina_${label}_id IF NOT EXISTS FOR (n:$label) REQUIRE n.id IS UNIQUE"
            )
            .consume()
        }
        schemaReady = true
      }
      Files.newBufferedReader(file).use { reader ->
        reader
          .lineSequence()
          .filter { it.isNotBlank() }
          .chunked(500)
          .forEach { lines ->
            val records = lines.map { Serializer.deserialize(it).properties() }

            session.executeWrite(
              { tx -> tx.run(WRITE_RECORDS, mapOf("records" to records)).consume() },
              TransactionConfig.builder().withTimeout(Duration.ofSeconds(10)).build(),
            )
          }
      }
    }

    Files.delete(file)
  }

  override fun close() {
    synchronized(appendLock) {
      if (closed) return

      closed = true
    }

    executor.execute { flush() }
    executor.shutdown()

    try {
      if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
        logger.warn { "trace shutdown timed out; undelivered observations remain in $spool" }

        executor.shutdownNow()
      }
    } catch (_: InterruptedException) {
      executor.shutdownNow()
      Thread.currentThread().interrupt()
    } finally {
      try {
        driver.close()
      } finally {
        synchronized(drainLock) {
          directoryLock.release()
          lockChannel.close()
        }
      }
    }
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
          Config.builder()
            .withConnectionTimeout(2, TimeUnit.SECONDS)
            .withConnectionAcquisitionTimeout(2, TimeUnit.SECONDS)
            .withMaxTransactionRetryTime(2, TimeUnit.SECONDS)
            .build(),
        )
      return Neo4jTraceRecorder(
        driver,
        Path.of(EnvironmentVariables.traceSpoolDirectory.get()),
        EnvironmentVariables.neo4jDatabase.get(),
      )
    }

    private val WRITE_RECORDS =
      """
      UNWIND ${'$'}records AS record
      MERGE (runtime:TraceRuntime {id: record.runtimeId})
      MERGE (instance:TraceInstance {id: record.instanceId})
      SET instance.name = record.instance, instance.stateMachine = record.stateMachine
      MERGE (instance)-[:IN_RUNTIME]->(runtime)
      MERGE (n:TraceRecord {id: record.id})
      SET n += record
      MERGE (n)-[:IN_INSTANCE]->(instance)
      FOREACH (_ IN CASE WHEN record.type = 'INSTANCE_CREATED' THEN [1] ELSE [] END | SET n:InstanceCreated)
      FOREACH (_ IN CASE WHEN record.type = 'STATE_ENTERED' THEN [1] ELSE [] END | SET n:StateEntered)
      FOREACH (_ IN CASE WHEN record.causalParent IS NOT NULL THEN [1] ELSE [] END |
        MERGE (parent:TraceRecord {id: record.causalParent})
        MERGE (n)-[:CAUSED_BY]->(parent)
      )
      FOREACH (_ IN CASE WHEN record.previousRecord IS NOT NULL THEN [1] ELSE [] END |
        MERGE (previous:TraceRecord {id: record.previousRecord})
        MERGE (previous)-[:NEXT]->(n)
      )
      FOREACH (_ IN CASE WHEN record.type = 'EVENT_EMITTED' THEN [1] ELSE [] END |
        SET n:EventEmitted
        MERGE (event:TraceEvent {id: record.eventId})
        SET event.topic = record.event, event.channel = record.channel,
            event.source = record.instance, event.target = record.target
        MERGE (n)-[:EMITS]->(event)
      )
      FOREACH (_ IN CASE WHEN record.type = 'EVENT_HANDLED' THEN [1] ELSE [] END |
        SET n:EventHandled
        MERGE (event:TraceEvent {id: record.eventId})
        SET event.topic = record.event, event.channel = record.channel,
            event.source = record.source, event.target = record.target
        MERGE (n)-[:HANDLES]->(event)
      )
      """
        .trimIndent()
  }
}
