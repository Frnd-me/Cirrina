package at.ac.uibk.dps.cirrina.core.object.guard;

import at.ac.uibk.dps.cirrina.core.lang.classes.guard.GuardClass;
import at.ac.uibk.dps.cirrina.core.object.expression.ExpressionBuilder;

/**
 * Guard builder, used to build guard objects.
 */
public final class GuardBuilder {

  /**
   * The guard class to build from.
   */
  private final GuardClass guardClass;

  /**
   * Initializes a guard builder.
   *
   * @param guardClass Guard class.
   */
  private GuardBuilder(GuardClass guardClass) {
    this.guardClass = guardClass;
  }

  /**
   * Creates a guard builder.
   *
   * @param guardClass Guard class.
   * @return Guard builder.
   */
  public static GuardBuilder from(GuardClass guardClass) {
    return new GuardBuilder(guardClass);
  }

  /**
   * Builds the guard.
   *
   * @return The built guard.
   * @throws IllegalArgumentException In case the guard could not be built.
   */
  public Guard build() throws IllegalArgumentException {
    return new Guard(guardClass.name, ExpressionBuilder.from(guardClass.expression).build());
  }
}
