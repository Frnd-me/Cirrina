package at.ac.uibk.dps.cirrina.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import at.ac.uibk.dps.cirrina.data.DefaultDescriptions;
import at.ac.uibk.dps.cirrina.lang.parser.Parser;
import at.ac.uibk.dps.cirrina.lang.parser.Parser.Options;
import at.ac.uibk.dps.cirrina.object.collaborativestatemachine.CollaborativeStateMachineBuilder;
import at.ac.uibk.dps.cirrina.object.context.InMemoryContext;
import at.ac.uibk.dps.cirrina.object.event.Event;
import at.ac.uibk.dps.cirrina.object.event.EventHandler;
import at.ac.uibk.dps.cirrina.object.statemachine.StateMachine;
import at.ac.uibk.dps.cirrina.runtime.scheduler.RoundRobinScheduler;
import java.net.URI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class RuntimeTest {

  private static StateMachine stateMachine;

  @BeforeAll
  public static void setUp() {
    var json = DefaultDescriptions.complete;

    var parser = new Parser(new Options());
    assertDoesNotThrow(() -> {
      var collaborativeStateMachine = CollaborativeStateMachineBuilder.from(parser.parse(json)).build();

      stateMachine = collaborativeStateMachine.getStateMachineByName("stateMachine1").get();
    });
  }

  @Test
  public void testExecute() {
    assertDoesNotThrow(() -> {
      var persistentContext = new InMemoryContext();

      var mockEventHandler = new EventHandler() {

        @Override
        public void close() throws Exception {

        }

        @Override
        public void sendEvent(URI source, Event event) {
          propagateEvent(event);
        }

        @Override
        public void subscribe(String topic) {

        }

        @Override
        public void unsubscribe(String topic) {

        }
      };

      var runtime = new Runtime(new RoundRobinScheduler(), mockEventHandler, persistentContext);

      var thread = new Thread(runtime);
      thread.start();

      runtime.newInstance(stateMachine);
    });
  }
}
