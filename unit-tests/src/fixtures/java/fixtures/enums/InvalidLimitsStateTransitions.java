package fixtures.enums;

import bot.den.foxflow.LimitsStateTransitions;
import bot.den.foxflow.StateMachine;
import bot.den.foxflow.tests.BasicEnum;

@StateMachine
public enum InvalidLimitsStateTransitions implements LimitsStateTransitions<BasicEnum> {
    A;

    @Override
    public boolean canTransitionState(BasicEnum newState) {
        return false;
    }
}
