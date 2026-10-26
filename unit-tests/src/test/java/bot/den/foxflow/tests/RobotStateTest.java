package bot.den.foxflow.tests;

import bot.den.foxflow.RobotState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.wpilib.hardware.hal.HAL;
import org.wpilib.hardware.hal.RobotMode;
import org.wpilib.simulation.DriverStationSim;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class RobotStateTest {
    @BeforeEach
    public void setup() {
        assertTrue(HAL.initialize());

        // Always try to start with a disabled state on tests
        setDriverStationState(RobotState.DISABLED);
    }

    @AfterEach
    public void cleanup() {
        Actions.reset();
    }

    @ParameterizedTest
    @MethodSource("robotStateCombinations")
    void robotTransitionWorksAsExpected(RobotState from, RobotState to) {
        var machine = new RobotRecordStateMachine(MultiStateEnum.A);

        // Tell the driver station sim what state we're in
        setDriverStationState(from);

        // This technically assumes DISABLED -> <other state> also works
        if(from != RobotState.DISABLED) {
            machine.poll();
        }

        // Verify we're in the correct starting state
        assertEquals(from, machine.currentState().robotState());

        // Update the driver station state
        setDriverStationState(to);

        // Verify we're still in the starting state
        assertEquals(from, machine.currentState().robotState());

        // Poll for changes
        machine.poll();

        // Verify we're in the correct ending state
        assertEquals(to, machine.currentState().robotState());
    }

    @ParameterizedTest
    @MethodSource("robotStateCombinations")
    void robotTransitionMovesAllFields(RobotState from, RobotState to) {
        var machine = new RobotRecordStateMachine(MultiStateEnum.A);

        // Make sure the robot and driver station state are correct
        setDriverStationState(from);
        if(from != RobotState.DISABLED) {
            machine.poll();
        }

        machine.state(MultiStateEnum.A, from).to(MultiStateEnum.B).transitionAlways();
        machine.state(MultiStateEnum.A).to(MultiStateEnum.C).transitionAlways();  // We should never see this transition

        // Update the driver station state and poll for changes
        setDriverStationState(to);
        machine.poll();

        assertEquals(to, machine.currentState().robotState());
        assertEquals(MultiStateEnum.B, machine.currentState().multiState());
    }

    @Test
    void robotStateIsNotHardCodedInToMethod() {
        AtomicBoolean test = new AtomicBoolean();

        var machine = new RobotRecordStateMachine(MultiStateEnum.A);

        machine.state(MultiStateEnum.A).to(RobotState.AUTO).run(Actions.runOnce(() -> test.set(true)));

        setDriverStationState(RobotState.AUTO);
        machine.poll();

        assertEquals(MultiStateEnum.A, machine.currentState().multiState());
        assertEquals(RobotState.AUTO, machine.currentState().robotState());

        assertTrue(test.get());
    }

    /**
     * This is a self-contained method that handles updating the Driver Station and ensuring the data is propagated in
     * a way that our code and the test can see it.
     *
     * @param state The state to set the driver station to
     */
    private static void setDriverStationState(RobotState state) {
        switch(state) {
            case DISABLED -> {
                DriverStationSim.setEnabled(false);
                DriverStationSim.setRobotMode(RobotMode.UNKNOWN);
            }
            case AUTO -> {
                DriverStationSim.setRobotMode(RobotMode.AUTONOMOUS);
                DriverStationSim.setEnabled(true);
                DriverStationSim.setDsAttached(true);
            }
            case TELEOP -> {
                DriverStationSim.setRobotMode(RobotMode.TELEOPERATED);
                DriverStationSim.setEnabled(true);
                DriverStationSim.setDsAttached(true);
            }
            case TEST -> {
                DriverStationSim.setRobotMode(RobotMode.UTILITY);
                DriverStationSim.setEnabled(true);
                DriverStationSim.setDsAttached(true);
            }
        }

        DriverStationSim.notifyNewData();
    }

    /**
     * @return Cross-product of all enum states
     */
    private static Stream<Arguments> robotStateCombinations() {
        return Arrays.stream(RobotState.values())
                .flatMap(a -> Arrays.stream(RobotState.values())
                        .map(b -> Arguments.arguments(a, b)));
    }
}
