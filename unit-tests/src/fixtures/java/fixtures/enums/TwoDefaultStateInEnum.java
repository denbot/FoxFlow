package fixtures.enums;

import bot.den.foxflow.DefaultState;
import bot.den.foxflow.StateMachine;

@StateMachine
public enum TwoDefaultStateInEnum {
    MY_VALUE,
    @DefaultState DEFAULT_VALUE,
    MY_OTHER_VALUE,
    @DefaultState OTHER_DEFAULT_VALUE
}
