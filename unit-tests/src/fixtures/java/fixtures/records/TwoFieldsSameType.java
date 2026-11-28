package fixtures.records;

import bot.den.foxflow.StateMachine;
import bot.den.foxflow.tests.BasicEnum;

@StateMachine
public record TwoFieldsSameType(
        BasicEnum basicEnumTypeA,
        BasicEnum basicEnumTypeB
) {
}
