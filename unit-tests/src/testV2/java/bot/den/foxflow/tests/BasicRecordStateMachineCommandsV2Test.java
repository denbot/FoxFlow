package bot.den.foxflow.tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wpilib.hardware.hal.HAL;
import org.wpilib.simulation.SimHooks;

import static org.junit.jupiter.api.Assertions.*;

public class BasicRecordStateMachineCommandsV2Test {
    private BasicRecordStateMachine machine;

    @BeforeEach
    public void setup() {
        assertTrue(HAL.initialize());

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
        Actions.reset();
    }

    @Test
    void triggerWorksOnSubsetOfData() {
        var trigger = this.machine
                .state(BasicEnum.STATE_A, BasicRecord.InnerEnum.CIRCLE)
                .trigger();

        // Make sure it isn't already triggered
        assertFalse(trigger.getAsBoolean());

        this.machine.transitionTo(MultiStateEnum.B, BasicEnum.STATE_A);

        // Trigger shouldn't be active yet even though it partially matches
        assertFalse(trigger.getAsBoolean());

        this.machine.transitionTo(BasicRecord.InnerEnum.CIRCLE);

        // Finally, this should be true
        assertTrue(trigger.getAsBoolean());
    }

    /**
     * This test was added because transition side effects were not taking into account the transition that had occurred
     * in the updateState transition. So (A1, B1) would update to (A2, B1), but if there was a transitionTo B2 in
     * response to that, we would see (A1, B2) instead of (A2, B2).
     * <p/>
     * TODO: Move this back to BasicRecordStateMachineTest when issue #16 is fixed. It only works under commands v2 due
     * to how the schedule command works.
     */
    @Test
    void callingTransitionToDuringStateTransitionWorks() {
        this.machine
                .state(MultiStateEnum.A)
                .to(MultiStateEnum.B)
                .transitionAlways()
                .run(
                        Actions.runOnce(() -> this.machine.transitionTo(BasicEnum.STATE_A))
                );

        this.machine.poll();
        assertEquals(MultiStateEnum.B, this.machine.currentState().multiState());
        assertEquals(BasicEnum.STATE_A, this.machine.currentState().basic());
    }
}
