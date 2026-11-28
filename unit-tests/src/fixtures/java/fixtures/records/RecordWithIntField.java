package fixtures.records;

import bot.den.foxflow.StateMachine;

@StateMachine
public record RecordWithIntField(
        int myField
) {
}
