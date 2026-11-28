package fixtures.enums;

import bot.den.foxflow.LimitsStateTransitions;
import bot.den.foxflow.StateMachine;

@StateMachine
public enum ArrayLimitsStateTransitions implements LimitsStateTransitions<String[]> {
    A;

    @Override
    public boolean canTransitionState(String[] newState) {
        return false;
    }
}
