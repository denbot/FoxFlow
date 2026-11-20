package bot.den.foxflow.builders.names;

import com.palantir.javapoet.ClassName;

public record Condition(
        ClassName name,
        ClassName always,
        ClassName when,
        ClassName after
) {
    public Condition(
            ClassName name
    ) {
        this(
                name,
                name.nestedClass("Always"),
                name.nestedClass("When"),
                name.nestedClass("After")
        );
    }
}
