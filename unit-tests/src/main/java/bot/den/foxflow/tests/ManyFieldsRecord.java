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
    enum A {
        Forward,
        Backward
    }

    enum B {
        Up,
        Down
    }

    enum C {
        Left,
        Right
    }

    enum D {
        Inside,
        Outside
    }

    enum E {
        Inverted,
        Outverted
    }
}
