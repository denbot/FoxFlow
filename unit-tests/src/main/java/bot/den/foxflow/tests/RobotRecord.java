package bot.den.foxflow.tests;

import bot.den.foxflow.RobotState;
import bot.den.foxflow.StateMachine;

@StateMachine
public record RobotRecord(
        MultiStateEnum multiState,
        RobotState robotState
) {
}
