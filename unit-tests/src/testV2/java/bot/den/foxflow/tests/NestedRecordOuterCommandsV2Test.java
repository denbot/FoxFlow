package bot.den.foxflow.tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wpilib.hardware.hal.HAL;

import static org.junit.jupiter.api.Assertions.*;

public class NestedRecordOuterCommandsV2Test {
    private NestedRecordOuterStateMachine machine;

    @BeforeEach
    public void setup() {
        assertTrue(HAL.initialize());

        this.machine = new NestedRecordOuterStateMachine(
                new NestedRecordOuter.NestedRecord(MultiStateEnum.A)
        );
    }

    @AfterEach
    public void cleanup() {
        Actions.reset();
    }

    @Test
    void triggerOnNestedRecordState() {
        // Create trigger for nested record state
        var trigger = this.machine
                .state(new NestedRecordOuter.NestedRecord(MultiStateEnum.B))
                .trigger();

        assertFalse(trigger.getAsBoolean());

        // Transition to the target state
        this.machine.transitionTo(new NestedRecordOuter.NestedRecord(MultiStateEnum.B));

        assertTrue(trigger.getAsBoolean());
    }
}
