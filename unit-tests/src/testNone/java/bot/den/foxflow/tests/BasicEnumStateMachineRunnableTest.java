package bot.den.foxflow.tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wpilib.hardware.hal.HAL;
import org.wpilib.simulation.SimHooks;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class BasicEnumStateMachineRunnableTest {

    @BeforeEach
    public void setup() {
        assertTrue(HAL.initialize());

        // Just for our timing tests, but it's a good thing to verify
        assertFalse(SimHooks.isTimingPaused());
    }

    @AfterEach
    public void cleanup() {
        Actions.reset();
    }

    @Test
    void commandWithRunnableAlwaysRunsOnlyOnce() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        final AtomicInteger test = new AtomicInteger(0);

        // Set up our state machine transition. Always run to trigger the move to a Command
        machine
                .state(BasicEnum.START)
                .to(BasicEnum.STATE_A)
                .transitionAlways()
                .run(
                        // run instead of runOnce would normally have the command run on every loop.
                        // Since we only have the Runnable, we only guarantee it runs once.
                        Actions.run(test::incrementAndGet)
                );

        // Test our initial state
        assertEquals(BasicEnum.START, machine.currentState());

        // Poll to see if the transitions happen
        machine.poll();

        // Verify the transition
        assertEquals(BasicEnum.STATE_A, machine.currentState());

        // This should have been called once so far.
        assertEquals(1, test.get());

        // Poll the machine just in case to ensure we still only have run it once.
        machine.poll();
        assertEquals(1, test.get());
    }
}
