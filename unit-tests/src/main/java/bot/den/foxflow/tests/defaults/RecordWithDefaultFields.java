package bot.den.foxflow.tests.defaults;

import bot.den.foxflow.StateMachine;

@StateMachine
public record RecordWithDefaultFields(
        DefaultLetter letter,
        DefaultNumber number,
        DefaultLatin latin
) {
}
