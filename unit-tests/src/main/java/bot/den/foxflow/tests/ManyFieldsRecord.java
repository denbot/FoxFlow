package bot.den.foxflow.tests;

import bot.den.foxflow.StateMachine;

/**
 * This record was added when we figured out the permutation builder was wrong for more than 3 elements.
 */
@StateMachine
public record ManyFieldsRecord(
        A a,
        B b,
        C c,
        D d,
        E e
) {
    public enum A {
        Forward,
        Backward
    }

    public enum B {
        Up,
        Down
    }

    public enum C {
        Left,
        Right
    }

    public enum D {
        Inside,
        Outside
    }

    public enum E {
        Inverted,
        Outverted
    }
}
