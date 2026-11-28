package bot.den.foxflow.tests;

import bot.den.foxflow.RobotState;
import bot.den.foxflow.StateMachine;

/**
 * This record exists so we can test how the robot state is handled when other enums limit state transitions.
 *
 * @param robotState
 * @param basicEnum
 */
@StateMachine
public record LimitingRobotRecord(
        RobotState robotState,
        BasicEnum basicEnum
) {
}
