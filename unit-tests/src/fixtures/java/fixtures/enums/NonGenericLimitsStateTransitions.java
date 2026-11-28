package fixtures.enums;

import bot.den.foxflow.LimitsStateTransitions;
import bot.den.foxflow.StateMachine;

@StateMachine
public enum NonGenericLimitsStateTransitions implements LimitsStateTransitions {
    A;

    @Override
    public boolean canTransitionState(Object newState) {
        return false;
    }
}
