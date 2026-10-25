package bot.den.foxflow.tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wpilib.command2.CommandScheduler;
import org.wpilib.command2.Commands;
import org.wpilib.command2.button.Trigger;
import org.wpilib.event.EventLoop;
import org.wpilib.hardware.hal.HAL;
import org.wpilib.simulation.SimHooks;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BasicEnumStateMachineCommandsV2Test {

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
    void basicTrigger() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        // Create our trigger
        Trigger trigger = machine.state(BasicEnum.STATE_A).trigger();

        // Ensure `poll` will force a state transition
        machine.state(BasicEnum.START).to(BasicEnum.STATE_A).transitionAlways();

        assertFalse(trigger.getAsBoolean());

        machine.poll();

        assertTrue(trigger.getAsBoolean());
    }

    @Test
    void triggerDefaultEventLoop() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        final AtomicBoolean test = new AtomicBoolean(false);

        // Create our trigger
        machine
                .state(BasicEnum.STATE_A)
                .trigger()
                .onTrue(
                        Actions.runOnce(() -> test.set(true)).ignoringDisable(true)
                );

        // Ensure `poll` will force a state transition
        machine.state(BasicEnum.START).to(BasicEnum.STATE_A).transitionAlways();

        machine.poll();
        Actions.poll();

        assertTrue(test.get());
    }

    @Test
    void triggerCustomEventLoop() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        final AtomicBoolean test = new AtomicBoolean(false);

        // Custom event loop
        EventLoop eventLoop = new EventLoop();

        // Create our trigger
        machine
                .state(BasicEnum.STATE_A)
                .trigger(eventLoop)
                .onTrue(
                        Actions.runOnce(() -> test.set(true))
                );

        // Ensure `poll` will force a state transition
        machine.state(BasicEnum.START).to(BasicEnum.STATE_A).transitionAlways();

        machine.poll();
        Actions.poll();

        // The default loop shouldn't have done anything
        assertFalse(test.get());

        // But our event loop poll will
        eventLoop.poll();

        assertTrue(test.get());
    }

    @Test
    void triggerOnlyRunsOnChange() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        final AtomicBoolean test = new AtomicBoolean(false);

        // Create our trigger
        machine
                .state(BasicEnum.START)
                .trigger()
                .onTrue(
                        Actions.runOnce(() -> test.set(true))
                );

        // There should be no state to transition, but we still need to make sure it doesn't affect the outcome
        machine.poll();
        CommandScheduler.getInstance().run();

        // The trigger did not run because START was the original state
        assertFalse(test.get());
    }
}
