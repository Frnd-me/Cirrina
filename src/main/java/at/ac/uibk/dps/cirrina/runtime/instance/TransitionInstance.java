package at.ac.uibk.dps.cirrina.runtime.instance;

import at.ac.uibk.dps.cirrina.object.transition.Transition;

public class TransitionInstance {

  private final Transition transition;

  private final StateMachineInstance parent;

  public TransitionInstance(Transition transition, StateMachineInstance parent) {
    this.transition = transition;
    this.parent = parent;
  }

  public Transition getTransition() {
    return transition;
  }
}
