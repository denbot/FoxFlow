package bot.den.state.tests;

import bot.den.state.RobotState;
import bot.den.state.StateMachine;

@StateMachine
public record RobotRecord(
        MultiStateEnum multiState,
        RobotState robotState
) {
}
