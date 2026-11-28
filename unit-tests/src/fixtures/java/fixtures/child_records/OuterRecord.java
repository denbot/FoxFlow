package fixtures.child_records;

import bot.den.foxflow.StateMachine;

@StateMachine
public record OuterRecord(
        InnerRecord myInnerRecord
) {
}
