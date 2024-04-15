package at.ac.uibk.dps.cirrina.core.object.statemachine;

import at.ac.uibk.dps.cirrina.core.io.plantuml.Exportable;
import at.ac.uibk.dps.cirrina.core.io.plantuml.PlantUmlVisitor;
import at.ac.uibk.dps.cirrina.core.lang.classes.context.ContextClass;
import at.ac.uibk.dps.cirrina.core.object.action.Action;
import at.ac.uibk.dps.cirrina.core.object.action.InvokeAction;
import at.ac.uibk.dps.cirrina.core.object.action.RaiseAction;
import at.ac.uibk.dps.cirrina.core.object.event.Event;
import at.ac.uibk.dps.cirrina.core.object.guard.Guard;
import at.ac.uibk.dps.cirrina.core.object.state.State;
import at.ac.uibk.dps.cirrina.core.object.transition.OnTransition;
import at.ac.uibk.dps.cirrina.core.object.transition.Transition;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.jgrapht.graph.DirectedPseudograph;

/**
 * Represents a state machine object, obtained by building a state machine class. This object encapsulates the structure and necessary
 * information for executing a state machine using a state machine executor. Multiple executors can be associated with one state machine
 * object, creating several state machine <i>instances</i>.
 * <p>
 * Formally, a state machine is a directed pseudograph, where states are vertices and transitions are edges.
 */
public final class StateMachine extends DirectedPseudograph<State, Transition> implements Exportable {

  private final List<StateMachine> nestedStateMachines;

  private final UUID id = UUID.randomUUID();

  private final String name;

  private final Optional<ContextClass> localContextClass;

  private final boolean abstractt;

  private final List<Guard> namedGuards;

  private final List<Action> namedActions;

  StateMachine(Parameters parameters) {
    super(Transition.class);

    this.name = parameters.name;
    this.localContextClass = parameters.localContextClass;
    this.namedGuards = Collections.unmodifiableList(parameters.namedGuards);
    this.namedActions = Collections.unmodifiableList(parameters.namedActions);
    this.abstractt = parameters.abstractt;
    this.nestedStateMachines = Collections.unmodifiableList(parameters.nestedStateMachines);
  }

  /**
   * Returns a state by its name. If not one state is known with the supplied name, empty is returned.
   *
   * @param stateName Name of the state to return.
   * @return The state with the supplied name or empty.
   * @throws IllegalArgumentException In case multiple states were found for the supplied name.
   */
  public Optional<State> findStateByName(String stateName) {
    // Attempt to match the provided name to a known state
    var states = vertexSet().stream()
        .filter(state -> state.getName().equals(stateName))
        .toList();

    if (states.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(states.getFirst());
  }

  /**
   * Returns the transitions from a state that are triggered by a given event name.
   *
   * @param fromState From state.
   * @param eventName The event name.
   * @return The list of on-transitions.
   */
  public List<OnTransition> findOnTransitionsFromStateByEventName(State fromState, String eventName) {
    return outgoingEdgesOf(fromState).stream()
        .filter(transition -> transition instanceof OnTransition)
        .map(transition -> (OnTransition) transition)
        .filter(transition -> transition.getEventName().equals(eventName))
        .toList();
  }

  /**
   * Returns the transitions from a state that are not event-triggered.
   *
   * @param fromState From state.
   * @return The list of always-transitions.
   */
  public List<Transition> findAlwaysTransitionsFromState(State fromState) {
    return outgoingEdgesOf(fromState).stream()
        .filter(transition -> !(transition instanceof OnTransition))
        .toList();
  }

  /**
   * Returns a guard by its name. If not one guard is known with the supplied name, empty is returned.
   *
   * @param guardName Name of the guard to return.
   * @return The guard with the supplied name or empty.
   */
  public Optional<Guard> findGuardByName(String guardName) {
    return namedGuards.stream()
        .filter(guard -> guard.getName().equals(Optional.of(guardName)))
        .findFirst();
  }

  /**
   * Returns an action by its name. If not one action is known with the supplied name, empty is returned.
   *
   * @param actionName Name of the action to return.
   * @return The action with the supplied name or empty.
   */
  public Optional<Action> findActionByName(String actionName) {
    return namedActions.stream()
        .filter(action -> action.getName().equals(Optional.of(actionName)))
        .findFirst();
  }

  public List<StateMachine> getNestedStateMachines() {
    return nestedStateMachines;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Optional<ContextClass> getLocalContextClass() {
    return localContextClass;
  }

  /**
   * Returns a boolean value which indicates whether this state machine is abstract.
   *
   * @return true, if this state machine is abstract.
   */
  public boolean isAbstract() {
    return abstractt;
  }

  /**
   * Returns the named actions of this state machine.
   *
   * @return named actions.
   */
  public List<Action> getNamedActions() {
    return namedActions;
  }

  /**
   * Returns the named guards of this state machine.
   *
   * @return named guards.
   */
  public List<Guard> getNamedGuards() {
    return namedGuards;
  }

  /**
   * Returns the initial state of this state machine.
   *
   * @return Initial state.
   */
  public State getInitialState() {
    return vertexSet().stream()
        .filter(State::isInitial)
        .findFirst()
        .get();
  }

  /**
   * Returns the collection of events handled by this state machine.
   *
   * @return Events handled by this state machine.
   */
  public List<String> getInputEvents() {
    return edgeSet().stream()
        .filter(OnTransition.class::isInstance)
        .map(onTransition -> ((OnTransition) onTransition).getEventName())
        .toList();
  }

  public List<Event> getOutputEvents() {
    return Stream.concat(
        // Raise action events
        Stream.concat(
            vertexSet().stream().flatMap(v -> v.getActionsOfType(RaiseAction.class).stream()),
            edgeSet().stream().flatMap(e -> e.getActionsOfType(RaiseAction.class).stream())
        ).map(RaiseAction::getEvent),

        // Invoke action events
        Stream.concat(
            vertexSet().stream().flatMap(v -> v.getActionsOfType(InvokeAction.class).stream()),
            edgeSet().stream().flatMap(e -> e.getActionsOfType(InvokeAction.class).stream())
        ).flatMap(invokeAction -> invokeAction.getDone().stream())
    ).toList();
  }

  public List<String> getStateNames() {
    return vertexSet().stream()
        .map(State::getName)
        .toList();
  }

  public List<String> getNamedGuardNames() {
    return namedGuards.stream()
        .map(Guard::getName)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
  }

  public List<String> getNamedActionNames() {
    return namedActions.stream()
        .map(Action::getName)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public void accept(PlantUmlVisitor visitor) {
    visitor.visit(this);
  }

  record Parameters(String name,
                    Optional<ContextClass> localContextClass,
                    List<Guard> namedGuards,
                    List<Action> namedActions,
                    boolean abstractt,
                    List<StateMachine> nestedStateMachines) {

  }
}
