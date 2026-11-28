package bot.den.foxflow.tests;

import bot.den.foxflow.RobotState;
import bot.den.foxflow.exceptions.InvalidStateTransition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wpilib.hardware.hal.HAL;

import static org.junit.jupiter.api.Assertions.*;

public class LimitingRobotStateTest {

    @BeforeEach
    public void setup() {
        assertTrue(HAL.initialize());

        // Always try to start with a disabled state on tests
        Util.setDriverStationState(RobotState.DISABLED);
    }

    @AfterEach
    public void cleanup() {
        Actions.reset();
    }

    @Test
    public void forcingInvalidTransitionDoesMoveRobotStateCorrectly() {
        var machine = new LimitingRobotRecordStateMachine(BasicEnum.START);
        // Weird looking transition you wouldn't see in the wild but is technically valid
        machine.state(RobotState.DISABLED).to(BasicEnum.END).transitionAlways();

        // Do this first so poll will move it over to teleop
        Util.setDriverStationState(RobotState.TELEOP);
        assertThrows(InvalidStateTransition.class, machine::poll);

        // Teleop had to survive, but we didn't allow the basic enum to move over
        assertEquals(RobotState.TELEOP, machine.currentState().robotState());
        assertEquals(BasicEnum.START, machine.currentState().basicEnum());
    }
}
