package bot.den.foxflow.tests;

import bot.den.foxflow.exceptions.AmbiguousTransitionSetup;
import bot.den.foxflow.exceptions.InvalidStateTransition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wpilib.hardware.hal.HAL;
import org.wpilib.simulation.SimHooks;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.wpilib.units.Units.Seconds;

public class BasicEnumStateMachineTest {
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
    void initialState() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        assertEquals(BasicEnum.START, machine.currentState());
    }

    @Test
    void whenClause() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        final AtomicBoolean test = new AtomicBoolean(false);

        // Set up our state machine transition
        machine.state(BasicEnum.START).to(BasicEnum.STATE_A).transitionWhen(test::get);

        // Test our initial state
        assertEquals(BasicEnum.START, machine.currentState());

        // Poll to see if any transitions happen
        machine.poll();

        // Ensure no transition happened
        assertEquals(BasicEnum.START, machine.currentState());

        // Update our test to allow the transition to pass
        test.set(true);

        // Check state again (to ensure `currentState` is just returning state and not checking anything)
        assertEquals(BasicEnum.START, machine.currentState());

        // Poll to see if the transition did happen this time
        machine.poll();

        // Verify the transition
        assertEquals(BasicEnum.STATE_A, machine.currentState());
    }

    @Test
    void always() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        // Set up our state machine transition
        machine.state(BasicEnum.START).to(BasicEnum.STATE_A).transitionAlways();

        // Test our initial state
        assertEquals(BasicEnum.START, machine.currentState());

        // Poll to see if the transitions happen
        machine.poll();

        // Verify the transition
        assertEquals(BasicEnum.STATE_A, machine.currentState());
    }

    @Test
    void invalidTransition() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        // START -> END is invalid and should fail immediately
        assertThrows(
                InvalidStateTransition.class,
                () -> machine.state(BasicEnum.START).to(BasicEnum.END)
        );
    }

    @Test
    void oneTransitionAtATime() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        // Set up two transitions to always run
        machine.state(BasicEnum.START).to(BasicEnum.STATE_A).transitionAlways();
        machine.state(BasicEnum.STATE_A).to(BasicEnum.STATE_B).transitionAlways();

        // Assert that we're at the start state
        assertEquals(BasicEnum.START, machine.currentState());

        // Move one state
        machine.poll();

        // Check the next state
        assertEquals(BasicEnum.STATE_A, machine.currentState());

        // Move one more state
        machine.poll();

        // Check the final state
        assertEquals(BasicEnum.STATE_B, machine.currentState());

        // No more state moves
        machine.poll();
        assertEquals(BasicEnum.STATE_B, machine.currentState());
    }

    @Test
    void transitionToCommand() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        machine.transitionTo(BasicEnum.STATE_A);

        assertEquals(BasicEnum.STATE_A, machine.currentState());
    }

    @Test
    void transitionToCommandOnlyRunsOnce() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        // Always jump from A to B on a machine.poll() call
        machine.state(BasicEnum.STATE_A).to(BasicEnum.STATE_B).transitionAlways();

        // Transition to STATE_A immediately
        machine.transitionTo(BasicEnum.STATE_A);

        // Verify we're at the A state
        assertEquals(BasicEnum.STATE_A, machine.currentState());

        // When we poll, the state should jump to B due to the always call above
        machine.poll();
        assertEquals(BasicEnum.STATE_B, machine.currentState());
    }

    @Test
    void transitionsStack() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        machine
                .state(BasicEnum.START)
                .to(BasicEnum.STATE_A)
                .transitionAlways()
                .run(Actions.runOnce(() -> machine.transitionTo(BasicEnum.STATE_B)));

        // Ensure creating the command didn't do anything
        assertEquals(BasicEnum.START, machine.currentState());

        // Both the transition to STATE_A and the subsequent transition to STATE_B should occur here
        machine.poll();

        assertEquals(BasicEnum.STATE_B, machine.currentState());
    }

    @Test
    void transitionToShortcut() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        machine
                .state(BasicEnum.START)
                .to(BasicEnum.STATE_A)
                .transitionAlways()
                .transitionTo(BasicEnum.STATE_B);

        // Ensure creating the command didn't do anything
        assertEquals(BasicEnum.START, machine.currentState());

        // Both the transition to STATE_A and the subsequent transition to STATE_B should occur here
        machine.poll();

        assertEquals(BasicEnum.STATE_B, machine.currentState());
    }

    @Test
    void invalidTransitionCannotBeForced() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        assertThrows(InvalidStateTransition.class, () -> machine.transitionTo(BasicEnum.END));

        assertEquals(BasicEnum.START, machine.currentState());
    }

    /**
     * Normally, you wouldn't transitionAlways and failLoudly on the same set of states. It can be useful at times when
     * you may know a particular transition is dangerous, and it would be entirely safer to crash the robot's software
     * stack than to risk the robot doing something physically dangerous.
     */
    @Test
    void failLoudlyPreventsOtherwiseSafeTransitions() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        machine.state(BasicEnum.START).to(BasicEnum.STATE_A).transitionAlways();
        machine.state(BasicEnum.START).to(BasicEnum.STATE_A).failLoudly();

        assertThrows(InvalidStateTransition.class, machine::poll);
    }

    @Test
    void multipleRunCommandsOnSameTransition() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        final AtomicBoolean first = new AtomicBoolean(false);
        final AtomicBoolean second = new AtomicBoolean(false);

        // Set up two commands on the same transition
        machine
                .state(BasicEnum.START)
                .to(BasicEnum.STATE_A)
                .transitionAlways()
                .run(Actions.runOnce(() -> first.set(true)));

        machine
                .state(BasicEnum.START)
                .to(BasicEnum.STATE_A)
                .run(Actions.runOnce(() -> second.set(true)));

        machine.poll();

        // Both commands should have been scheduled
        assertTrue(first.get());
        assertTrue(second.get());
        assertEquals(BasicEnum.STATE_A, machine.currentState());
    }

    @Test
    void multipleTransitionWhenOnSameTransition() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        final AtomicBoolean firstCondition = new AtomicBoolean(false);
        final AtomicBoolean secondCondition = new AtomicBoolean(false);

        // Set up multiple conditions for the same transition
        machine.state(BasicEnum.START).to(BasicEnum.STATE_A).transitionWhen(firstCondition::get);
        machine.state(BasicEnum.START).to(BasicEnum.STATE_A).transitionWhen(secondCondition::get);

        // Neither condition is true yet
        machine.poll();
        assertEquals(BasicEnum.START, machine.currentState());

        // Enable one condition
        firstCondition.set(true);
        machine.poll();
        assertEquals(BasicEnum.STATE_A, machine.currentState());
    }

    @Test
    void canAddTransitionWhileMachineIsRunning() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        // Set up first transition
        machine.state(BasicEnum.START).to(BasicEnum.STATE_A).transitionAlways();

        machine.poll();
        assertEquals(BasicEnum.STATE_A, machine.currentState());

        // Add a new transition dynamically
        machine.state(BasicEnum.STATE_A).to(BasicEnum.STATE_B).transitionAlways();

        machine.poll();
        assertEquals(BasicEnum.STATE_B, machine.currentState());
    }

    @Test
    void addingTransitionFromCurrentStateActivatesImmediately() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        final AtomicBoolean condition = new AtomicBoolean(true);

        // Add transition while already in the starting state
        machine.state(BasicEnum.START).to(BasicEnum.STATE_A).transitionWhen(condition::get);

        // Should transition immediately on next poll
        machine.poll();
        assertEquals(BasicEnum.STATE_A, machine.currentState());
    }

    @Test
    void transitionAfterTimeInSeconds() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        // Testing the ability to start a timer after hitting a particular state means we need to set everything up
        // before being in the state that will start the timer.
        machine.state(BasicEnum.START).to(BasicEnum.STATE_A).transitionAlways();

        // This is the method we're actually testing
        machine.state(BasicEnum.STATE_A).to(BasicEnum.STATE_B).transitionAfter(20);

        // Polling should start our timer and put us into STATE_A
        machine.poll();
        assertEquals(BasicEnum.STATE_A, machine.currentState());

        // Our timer goes for 20 seconds. Let's jump 19 of them and make sure we haven't changed state
        SimHooks.stepTiming(19);
        machine.poll();
        assertEquals(BasicEnum.STATE_A, machine.currentState());

        // Now we jump 1 more second and verify the transition occurred
        SimHooks.stepTiming(1);
        machine.poll();
        assertEquals(BasicEnum.STATE_B, machine.currentState());
    }

    @Test
    void transitionAfterTimeInTimeUnit() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        // Testing the ability to start a timer after hitting a particular state means we need to set everything up
        // before being in the state that will start the timer.
        machine.state(BasicEnum.START).to(BasicEnum.STATE_A).transitionAlways();

        // This is the method we're actually testing
        machine.state(BasicEnum.STATE_A).to(BasicEnum.STATE_B).transitionAfter(Seconds.of(20));

        // Polling should start our timer and put us into STATE_A
        machine.poll();
        assertEquals(BasicEnum.STATE_A, machine.currentState());

        // Our timer goes for 20 seconds. Let's jump 19 of them and make sure we haven't changed state
        SimHooks.stepTiming(19);
        machine.poll();
        assertEquals(BasicEnum.STATE_A, machine.currentState());

        // Now we jump 1 more second and verify the transition occurred
        SimHooks.stepTiming(1);
        machine.poll();
        assertEquals(BasicEnum.STATE_B, machine.currentState());
    }

    @Test
    void transitionAfterStartsImmediately() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);

        // This is the method we're actually testing
        machine.state(BasicEnum.START).to(BasicEnum.STATE_A).transitionAfter(Seconds.of(20));

        // Our timer goes for 20 seconds. Let's jump 19 of them and make sure we haven't changed state
        SimHooks.stepTiming(19);
        machine.poll();
        assertEquals(BasicEnum.START, machine.currentState());

        // Now we jump 1 more second and verify the transition occurred
        SimHooks.stepTiming(1);
        machine.poll();
        assertEquals(BasicEnum.STATE_A, machine.currentState());
    }

    @Test
    void transitionAfterAmbiguousStateTransitionCannotBeSetup() {
        var machine = new BasicEnumStateMachine(BasicEnum.STATE_A);

        machine.state(BasicEnum.STATE_A).to(BasicEnum.STATE_B).transitionAfter(Seconds.of(5));

        assertThrows(
                AmbiguousTransitionSetup.class,
                () -> machine.state(BasicEnum.STATE_A).to(BasicEnum.STATE_C).transitionAfter(Seconds.of(1))
        );
    }

    @Test
    void verifyCurrentTopicIsBasedOnStateMachineName() {
        var machine = new BasicEnumStateMachine(BasicEnum.STATE_A);

        assertEquals("FoxFlow/BasicEnum/State", machine.currentStateTopic.getName());
    }

    @Test
    void changingStateUpdatesSubscription() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);
        var subscriber = machine.currentStateTopic.subscribe(BasicEnum.STATE_D.name());

        assertEquals(BasicEnum.START.toString(), subscriber.get());
        machine.transitionTo(BasicEnum.STATE_A);

        assertEquals(BasicEnum.STATE_A.toString(), subscriber.get());
    }
}
