package bot.den.foxflow.tests;

import bot.den.foxflow.RobotState;
import bot.den.foxflow.StateMachine;

@StateMachine
public record NestedRecordOuter(
        RobotState robotState,
        NestedRecord nested
) {
    public record NestedRecord(MultiStateEnum multiStateEnum) {

    }
}
