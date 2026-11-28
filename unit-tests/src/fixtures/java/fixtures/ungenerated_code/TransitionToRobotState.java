package fixtures.ungenerated_code;

import bot.den.foxflow.RobotState;
import bot.den.foxflow.tests.RobotRecordStateMachine;
import bot.den.foxflow.tests.MultiStateEnum;


class TransitionToRobotState {
    void badTransitionSetup() {
        var machine = new RobotRecordStateMachine(MultiStateEnum.A);

        machine.transitionTo(RobotState.TELEOP);
    }
}