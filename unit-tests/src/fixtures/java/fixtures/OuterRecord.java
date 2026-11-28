package fixtures;

import bot.den.foxflow.StateMachine;

@StateMachine
public record OuterRecord(
        RecordWithIntField myIntField
) {
}
