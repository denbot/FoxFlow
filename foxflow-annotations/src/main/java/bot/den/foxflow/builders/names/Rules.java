package bot.den.foxflow.builders.names;

import com.palantir.javapoet.ClassName;

public record Rules(
        // Name of the rules
        ClassName name,

        // Conditions
        Condition condition,

        // Actions
        Action action
) {
    public Rules(
            String obfuscatedPackageName
    ) {
        var rulesName = ClassName.get(obfuscatedPackageName, "Rules");
        var conditionName = rulesName.nestedClass("Condition");
        var actionName = rulesName.nestedClass("Action");

        this(
                rulesName,
                new Condition(conditionName),
                new Action(actionName)
        );
    }
}
