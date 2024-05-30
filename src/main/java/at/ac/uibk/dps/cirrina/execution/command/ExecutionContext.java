package at.ac.uibk.dps.cirrina.execution.command;

import at.ac.uibk.dps.cirrina.execution.object.event.EventListener;
import at.ac.uibk.dps.cirrina.execution.object.statemachine.StateMachineEventHandler;
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector;
import at.ac.uibk.dps.cirrina.tracing.Gauges;

public record ExecutionContext(
    Scope scope,
    ServiceImplementationSelector serviceImplementationSelector,
    StateMachineEventHandler eventHandler,
    EventListener eventListener,
    Gauges gauges,
    boolean isWhile
) {

  public ExecutionContext withScope(Scope scope) {
    return new ExecutionContext(scope, serviceImplementationSelector, eventHandler,
        eventListener, gauges, isWhile);
  }

  public ExecutionContext withIsWhile(boolean isWhile) {
    return new ExecutionContext(scope, serviceImplementationSelector, eventHandler,
        eventListener, gauges, isWhile);
  }
}
