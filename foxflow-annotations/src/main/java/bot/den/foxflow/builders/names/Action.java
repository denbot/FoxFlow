package bot.den.foxflow.builders.names;

import com.palantir.javapoet.ClassName;

public record Action(
        ClassName name,
        ClassName transitionTo,
        ClassName runCommand
        ) {

    public Action(
            ClassName name
    ) {
        this(
                name,
                name.nestedClass("TransitionTo"),
                name.nestedClass("RunCommand")
        );
    }
}
