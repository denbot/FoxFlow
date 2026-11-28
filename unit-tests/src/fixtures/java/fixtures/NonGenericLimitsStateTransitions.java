package fixtures;

import bot.den.foxflow.LimitsStateTransitions;
import bot.den.foxflow.StateMachine;
import bot.den.foxflow.tests.BasicEnum;

@StateMachine
public enum NonGenericLimitsStateTransitions implements LimitsStateTransitions {
    A;

    @Override
    public boolean canTransitionState(Object newState) {
        return false;
    }
}
