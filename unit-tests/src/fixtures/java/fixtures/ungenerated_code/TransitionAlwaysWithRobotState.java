package fixtures.ungenerated_code;

import bot.den.foxflow.RobotState;
import bot.den.foxflow.tests.RobotRecordStateMachine;
import bot.den.foxflow.tests.MultiStateEnum;


class TransitionAlwaysWithRobotState {
    void badTransitionSetup() {
        var machine = new RobotRecordStateMachine(MultiStateEnum.A);

        machine.state(RobotState.DISABLED).to(RobotState.TELEOP).transitionAlways();
    }
}