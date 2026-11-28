package fixtures;

import bot.den.foxflow.LimitsStateTransitions;
import bot.den.foxflow.StateMachine;
import bot.den.foxflow.tests.BasicEnum;

@StateMachine
public enum ArrayLimitsStateTransitions implements LimitsStateTransitions<String[]> {
    A;

    @Override
    public boolean canTransitionState(String[] newState) {
        return false;
    }
}
