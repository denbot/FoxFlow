package bot.den.foxflow.tests;

import bot.den.foxflow.StateMachine;

/**
 * This class is kind of mean. It's designed to trip up assumptions throughout the code base, such as using only the
 * field name in methods that use "data" or "state" as parameters. We don't actually test the generated state machine
 * directly.
 *
 * @param data
 * @param state
 */
@StateMachine
public record TrippyNamesRecord(
        BasicEnum data,
        MultiStateEnum state
) {
}
