package bot.den.foxflow.tests;

import bot.den.foxflow.exceptions.InvalidStateTransition;
import bot.den.foxflow.tests.BasicRecord.InnerEnum;
import edu.wpi.first.hal.HAL;
import edu.wpi.first.wpilibj.simulation.SimHooks;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import edu.wpi.first.wpilibj2.command.Commands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class BasicRecordStateMachineTest {
    private BasicRecordStateMachine machine;

    @BeforeEach
    public void setup() {
        assertTrue(HAL.initialize(500, 0));

        this.machine = new BasicRecordStateMachine(
                MultiStateEnum.A,
                BasicEnum.START,
                BasicRecord.InnerEnum.STAR
        );

        // Just for our timing tests, but it's a good thing to verify
        assertFalse(SimHooks.isTimingPaused());
    }

    @AfterEach
    public void cleanup() {
        // This method runs after each test to reset the scheduler state
        CommandScheduler.getInstance().cancelAll();
        CommandScheduler.getInstance().run(); // Call run() to execute end() methods
    }


    @Test
    void canCreateStateMachine() {
        // Technically we create the machine in `setup`, but we do want to verify the state here

        var state = this.machine.currentState();
        assertEquals(MultiStateEnum.A, state.multiState());
        assertEquals(BasicEnum.START, state.basic());
        assertEquals(BasicRecord.InnerEnum.STAR, state.inner());
    }

    @Test
    void canTransitionGivenPartialSpecifiers() {
        this.machine.state(BasicEnum.START).to(BasicEnum.STATE_A).transitionAlways();

        // Verify nothing changed
        var state = this.machine.currentState();
        assertEquals(MultiStateEnum.A, state.multiState());
        assertEquals(BasicEnum.START, state.basic());
        assertEquals(BasicRecord.InnerEnum.STAR, state.inner());

        // Start the transitions
        this.machine.poll();

        // Verify the new state
        state = this.machine.currentState();
        assertEquals(MultiStateEnum.A, state.multiState());
        assertEquals(BasicEnum.STATE_A, state.basic());
        assertEquals(BasicRecord.InnerEnum.STAR, state.inner());
    }

    @Test
    void canTransitionDifferentComponentsUsingPartialSpecifiers() {
        this.machine.state(BasicEnum.START).to(MultiStateEnum.B).transitionAlways();

        // Verify nothing changed
        var state = this.machine.currentState();
        assertEquals(MultiStateEnum.A, state.multiState());
        assertEquals(BasicEnum.START, state.basic());
        assertEquals(BasicRecord.InnerEnum.STAR, state.inner());

        // Start the transitions
        this.machine.poll();

        // Verify the new state
        state = this.machine.currentState();
        assertEquals(MultiStateEnum.B, state.multiState());
        assertEquals(BasicEnum.START, state.basic());
        assertEquals(BasicRecord.InnerEnum.STAR, state.inner());
    }

    @Test
    void canLimitTransitionUsingMultipleSpecifiers() {
        // This shouldn't transition until the state has both of the required states
        this.machine
                .state(MultiStateEnum.B, BasicEnum.STATE_A)
                .to(BasicRecord.InnerEnum.CIRCLE)
                .transitionAlways();

        // No transition should occur here
        this.machine.poll();

        // Verify nothing changed
        var state = this.machine.currentState();
        assertEquals(MultiStateEnum.A, state.multiState());
        assertEquals(BasicEnum.START, state.basic());
        assertEquals(BasicRecord.InnerEnum.STAR, state.inner());

        // Force a transition for one part of the specifier
        var twoStateCommand = this.machine.transitionTo(MultiStateEnum.B);
        CommandScheduler.getInstance().schedule(twoStateCommand);

        // Verify our partial state change
        state = this.machine.currentState();
        assertEquals(MultiStateEnum.B, state.multiState());
        assertEquals(BasicEnum.START, state.basic());
        assertEquals(BasicRecord.InnerEnum.STAR, state.inner());

        // Force a transition for the other part of the specifier
        var basicCommand = this.machine.transitionTo(BasicEnum.STATE_A);
        CommandScheduler.getInstance().schedule(basicCommand);

        // Verify our partial state change
        state = this.machine.currentState();
        assertEquals(MultiStateEnum.B, state.multiState());
        assertEquals(BasicEnum.STATE_A, state.basic());
        assertEquals(BasicRecord.InnerEnum.STAR, state.inner());

        // Poll should now transition us to the final change
        this.machine.poll();

        // Verify the final change
        state = this.machine.currentState();
        assertEquals(MultiStateEnum.B, state.multiState());
        assertEquals(BasicEnum.STATE_A, state.basic());
        assertEquals(BasicRecord.InnerEnum.CIRCLE, state.inner());
    }

    @Test
    void triggerWorksOnSubsetOfData() {
        var trigger = this.machine
                .state(BasicEnum.STATE_A, BasicRecord.InnerEnum.CIRCLE)
                .trigger();

        // Make sure it isn't already triggered
        assertFalse(trigger.getAsBoolean());

        var firstCommand = this.machine.transitionTo(MultiStateEnum.B, BasicEnum.STATE_A);
        CommandScheduler.getInstance().schedule(firstCommand);

        // Trigger shouldn't be active yet even though it partially matches
        assertFalse(trigger.getAsBoolean());

        var secondCommand = this.machine.transitionTo(BasicRecord.InnerEnum.CIRCLE);
        CommandScheduler.getInstance().schedule(secondCommand);

        // Finally, this should be true
        assertTrue(trigger.getAsBoolean());
    }

    @Test
    void whenWorksOnSubsetOfData() {
        final AtomicBoolean test = new AtomicBoolean(false);

        this.machine
                .state(BasicEnum.STATE_A)
                .to(BasicEnum.STATE_B)
                .transitionWhen(test::get);

        // Nothing should happen as our boolean isn't set AND we're not in the correct starting state
        this.machine.poll();
        assertEquals(BasicEnum.START, this.machine.currentState().basic());

        // Now let's try setting our trigger
        test.set(true);

        // Nothing should happen still as we aren't in the right starting spot
        this.machine.poll();
        assertEquals(BasicEnum.START, this.machine.currentState().basic());

        // Reset our flag and move the state over manually
        test.set(false);
        var command = this.machine.transitionTo(BasicEnum.STATE_A);
        CommandScheduler.getInstance().schedule(command);

        // Verify we've only moved to A, even after polling
        assertEquals(BasicEnum.STATE_A, this.machine.currentState().basic());
        this.machine.poll();
        assertEquals(BasicEnum.STATE_A, this.machine.currentState().basic());

        // One more time, let's set our trigger
        test.set(true);
        assertEquals(BasicEnum.STATE_A, this.machine.currentState().basic());

        // Polling should move it over
        this.machine.poll();
        assertEquals(BasicEnum.STATE_B, this.machine.currentState().basic());
    }

    @Test
    void addingANewSpecifierTransitionAlways() {
        this.machine
                .state(BasicEnum.STATE_A)
                .to(BasicEnum.STATE_B)
                .transitionAlways();

        // Nothing should happen yet
        this.machine.poll();
        assertEquals(BasicEnum.START, this.machine.currentState().basic());

        // Now we set up our transition from START -> STATE_A
        this.machine
                .state(BasicEnum.START)
                .to(BasicEnum.STATE_A)
                .transitionAlways();

        // This should transition once to A
        this.machine.poll();
        assertEquals(BasicEnum.STATE_A, this.machine.currentState().basic());

        // This should transition once more to B
        this.machine.poll();
        assertEquals(BasicEnum.STATE_B, this.machine.currentState().basic());
    }

    @Test
    void failLoudlyOnPartialRecordTransition() {
        // Set up failLoudly on a partial state match
        this.machine
                .state(BasicEnum.START)
                .to(BasicEnum.STATE_A)
                .failLoudly();

        // Force the transition and expect it to fail
        assertThrows(InvalidStateTransition.class,
                () -> CommandScheduler.getInstance().schedule(this.machine.transitionTo(BasicEnum.STATE_A)));
    }

    @Test
    void failLoudlyWithRunCommand() {
        final AtomicBoolean commandRan = new AtomicBoolean(false);

        // Set up transition with run command
        this.machine
                .state(BasicEnum.START)
                .to(BasicEnum.STATE_A)
                .transitionAlways()
                .run(Commands.runOnce(() -> commandRan.set(true)).ignoringDisable(true));

        // Set up failLoudly on the same transition
        this.machine
                .state(BasicEnum.START)
                .to(BasicEnum.STATE_A)
                .failLoudly();

        // Transition should fail
        assertThrows(InvalidStateTransition.class, this.machine::poll);

        // Command should not run when failLoudly prevents the transition
        assertFalse(commandRan.get());
    }

    @Test
    void independentTransitionsProcessInSamePoll() {
        // These are two transitions set up separately, but they are independent of each other and should move together
        this.machine.state(MultiStateEnum.A).to(MultiStateEnum.B).transitionAlways();
        this.machine.state(BasicEnum.START).to(BasicEnum.STATE_A).transitionAlways();

        this.machine.poll();

        // After a single poll, they both should match
        assertEquals(MultiStateEnum.B, this.machine.currentState().multiState());
        assertEquals(BasicEnum.STATE_A, this.machine.currentState().basic());
    }

    @Test
    void independentTransitionChoosesTheLargestStateToTransitionOn() {
        // These are two transitions set up separately, but specifying two states in the second qualifier means that one wins
        this.machine.state(MultiStateEnum.A).to(MultiStateEnum.B).transitionAlways();
        // Specify MultiStateEnum in the output so these two transitions aren't compatible
        this.machine.state(MultiStateEnum.A, BasicEnum.START).to(MultiStateEnum.C, BasicEnum.STATE_A).transitionAlways();

        this.machine.poll();

        // After a single poll, only the one should have moved
        assertEquals(MultiStateEnum.C, this.machine.currentState().multiState());
        assertEquals(BasicEnum.STATE_A, this.machine.currentState().basic());
    }

    @Test
    void independentTransitionChoosesLargestStateToTransitionOn() {
        AtomicBoolean test = new AtomicBoolean(true);

        // These are two transitions set up separately, but specifying two states in the second qualifier means that one wins
        // It is important to note that having different transition function causes them to combine in the get nextStage
        this.machine.state(MultiStateEnum.A, BasicEnum.START, InnerEnum.STAR).to(MultiStateEnum.B).transitionAlways();
        this.machine.state(MultiStateEnum.A, BasicEnum.START).to(MultiStateEnum.C).transitionWhen(test::get);

        this.machine.poll();

        // The one with more specifiers should have won
        assertEquals(MultiStateEnum.B, this.machine.currentState().multiState());
        assertEquals(BasicEnum.START, this.machine.currentState().basic());
        assertEquals(InnerEnum.STAR, this.machine.currentState().inner());
    }

    /**
     * This test was added because we realized the "state" or "to" methods were not created for this particular
     * permutation of our record components.
     */
    @Test
    void verifyAllRecordsGetCreated() {
        this.machine.state(MultiStateEnum.A, InnerEnum.STAR).to(MultiStateEnum.B, InnerEnum.CIRCLE).transitionAlways();

        this.machine.poll();

        assertEquals(MultiStateEnum.B, this.machine.currentState().multiState());
        assertEquals(BasicEnum.START, this.machine.currentState().basic());
        assertEquals(InnerEnum.CIRCLE, this.machine.currentState().inner());
    }

    @Test
    void multiplePossibleTransitionsShouldChooseTheClosestTime() {
        this.machine.state(BasicEnum.START).to(BasicEnum.STATE_A).transitionAfter(5);
        this.machine.state(InnerEnum.STAR).to(InnerEnum.CIRCLE).transitionAfter(3);

        // Let's move forward 2 seconds to make sure nothing has happened
        SimHooks.stepTiming(2);
        this.machine.poll();

        assertEquals(BasicEnum.START, this.machine.currentState().basic());
        assertEquals(InnerEnum.STAR, this.machine.currentState().inner());

        // One more second should transition STAR -> CIRCLE
        SimHooks.stepTiming(1);
        this.machine.poll();

        assertEquals(BasicEnum.START, this.machine.currentState().basic());
        assertEquals(InnerEnum.CIRCLE, this.machine.currentState().inner());

        // Great, and our original timer is still valid, so after 2 more seconds we should see it transition too
        SimHooks.stepTiming(2);
        this.machine.poll();

        assertEquals(BasicEnum.STATE_A, this.machine.currentState().basic());
        assertEquals(InnerEnum.CIRCLE, this.machine.currentState().inner());
    }

    @Test
    void sameTimeWithDifferentTransitionsChoosesTheLargestFromState() {
        // These are two transitions set up separately, but specifying two states in the second qualifier means that one wins
        this.machine.state(MultiStateEnum.A).to(MultiStateEnum.B).transitionAfter(5);
        // Specify MultiStateEnum in the output so these two transitions aren't compatible
        this.machine.state(MultiStateEnum.A, BasicEnum.START).to(MultiStateEnum.C, BasicEnum.STATE_A).transitionAfter(5);

        // Start by making sure nothing moved
        this.machine.poll();
        assertEquals(MultiStateEnum.A, this.machine.currentState().multiState());
        assertEquals(BasicEnum.START, this.machine.currentState().basic());

        // Move 5 seconds forward and make sure we transitioned the correct state
        SimHooks.stepTiming(5);
        this.machine.poll();

        assertEquals(MultiStateEnum.C, this.machine.currentState().multiState());
        assertEquals(BasicEnum.STATE_A, this.machine.currentState().basic());
    }
}
