package at.ac.uibk.dps.cirrina.execution.trace

import at.ac.uibk.dps.cirrina.EnvironmentVariables
import dagger.Module
import dagger.Provides
import jakarta.inject.Singleton

@Module
class TraceRecorderModule {
  @Provides
  @Singleton
  fun provideTraceRecorder(): TraceRecorder =
    when (EnvironmentVariables.traceRecorder.get()?.uppercase()) {
      "NEO4J" -> Neo4jTraceRecorder.create()
      null -> TraceRecorder.noop
      else -> error("invalid value for environment variable TRACE_RECORDER")
    }
}
