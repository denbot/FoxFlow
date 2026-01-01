package bot.den.state.tests;

import bot.den.state.StateMachine;

@StateMachine
public record BasicRecord(
        TwoStateEnum twoState,
        BasicEnum basic,
        InnerEnum inner
) {
    enum InnerEnum {
        STAR, CIRCLE, SQUARE;
    }
}
