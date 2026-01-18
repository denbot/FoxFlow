package bot.den.foxflow.tests;

import bot.den.foxflow.StateMachine;

@StateMachine
public record BasicRecord(
        MultiStateEnum multiState,
        BasicEnum basic,
        InnerEnum inner
) {
    public enum InnerEnum {
        STAR, CIRCLE, SQUARE;
    }
}
