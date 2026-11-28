package fixtures.records;

import bot.den.foxflow.StateMachine;

@StateMachine
public record GenericRecord<T extends Enum<T>>(
        T myGenericField
) {
}
