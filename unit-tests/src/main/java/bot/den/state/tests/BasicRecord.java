package bot.den.state.tests;

import bot.den.state.CanTransitionState;
import bot.den.state.StateMachine;

@StateMachine
public record BasicRecord(
        TwoStateEnum twoState,
        BasicEnum basic,
        InnerEnum inner
) {
    enum InnerEnum implements CanTransitionState<InnerEnum> {
        STAR, CIRCLE, SQUARE;

        @Override
        public boolean canTransitionTo(InnerEnum newState) {
            // Everything goes
            return true;
        }
    }
}
