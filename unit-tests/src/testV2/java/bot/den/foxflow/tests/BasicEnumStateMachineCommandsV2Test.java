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

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void commandInitialize() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        final AtomicBoolean test = new AtomicBoolean(false);

        // Set up our state machine transition. Always run to trigger the move to a Command
        machine
                .state(BasicEnum.START)
                .to(BasicEnum.STATE_A)
                .transitionAlways()
                .run(
                        Actions.runOnce(() -> test.set(true))
                );

        // Test our initial state
        assertEquals(BasicEnum.START, machine.currentState());

        // Poll to see if the transitions happen
        machine.poll();

        // Verify the transition
        assertEquals(BasicEnum.STATE_A, machine.currentState());

        // The runOnce command runs in the initializer and therefore runs upon being scheduled
        assertTrue(test.get());
    }

    @Test
    void commandRun() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        final AtomicBoolean test = new AtomicBoolean(false);

        // Set up our state machine transition. Always run to trigger the move to a Command
        machine
                .state(BasicEnum.START)
                .to(BasicEnum.STATE_A)
                .transitionAlways()
                .run(
                        Actions.run(() -> test.set(true))
                );

        // Test our initial state
        assertEquals(BasicEnum.START, machine.currentState());

        // Poll to see if the transitions happen
        machine.poll();

        // Verify the transition
        assertEquals(BasicEnum.STATE_A, machine.currentState());

        // The run command runs in the execute loop and therefore needs the scheduler
        assertFalse(test.get());

        Actions.poll();

        // Now it should be true
        assertTrue(test.get());
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

    @Test
    void triggerDeactivatesWhenLeavingState() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        // Create trigger for initial state
        var trigger = machine.state(BasicEnum.START).trigger();

        assertTrue(trigger.getAsBoolean());

        // Transition away from START
        machine.state(BasicEnum.START).to(BasicEnum.STATE_A).transitionAlways();
        machine.poll();

        assertFalse(trigger.getAsBoolean());

        // Transition back to START
        machine.transitionTo(BasicEnum.START);

        assertTrue(trigger.getAsBoolean());
    }

    @Test
    void multipleTriggersSameMachine() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        // Create triggers for different states
        var startTrigger = machine.state(BasicEnum.START).trigger();
        var aTrigger = machine.state(BasicEnum.STATE_A).trigger();

        assertTrue(startTrigger.getAsBoolean());
        assertFalse(aTrigger.getAsBoolean());

        // Transition to STATE_A
        machine.state(BasicEnum.START).to(BasicEnum.STATE_A).transitionAlways();
        machine.poll();

        // Only the STATE_A trigger should be active
        assertFalse(startTrigger.getAsBoolean());
        assertTrue(aTrigger.getAsBoolean());
    }
}
