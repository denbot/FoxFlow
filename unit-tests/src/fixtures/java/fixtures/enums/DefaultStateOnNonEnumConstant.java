package fixtures.enums;

import bot.den.foxflow.DefaultState;

public enum DefaultStateOnNonEnumConstant {
    ;

    @DefaultState
    public static final int NOT_AN_ENUM_CONSTANT = 5;
}
