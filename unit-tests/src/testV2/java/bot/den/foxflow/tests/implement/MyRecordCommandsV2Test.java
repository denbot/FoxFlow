package bot.den.foxflow.tests.implement;

import bot.den.foxflow.tests.Actions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wpilib.hardware.hal.HAL;

import static org.junit.jupiter.api.Assertions.*;

public class MyRecordCommandsV2Test {
    @BeforeEach
    public void setup() {
        assertTrue(HAL.initialize());
    }

    @AfterEach
    public void cleanup() {
        Actions.reset();
    }

    @Test
    void triggerWorksWithInterfaces() {
        var machine = new MyRecordStateMachine(Shapes.SQUARE);

        // Create trigger for a different implementation type
        var trigger = machine.state(Flavors.Vanilla).trigger();

        assertFalse(trigger.getAsBoolean());

        // Transition to the target state
        machine.transitionTo(Flavors.Vanilla);

        assertTrue(trigger.getAsBoolean());
    }

}
