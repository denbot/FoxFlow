package fixtures;

import bot.den.foxflow.StateMachine;

@StateMachine
public record RecordWithStringField(
        String myField
) {
}
