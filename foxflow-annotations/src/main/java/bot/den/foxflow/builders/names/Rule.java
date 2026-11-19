package bot.den.foxflow.builders.names;

import com.palantir.javapoet.ClassName;

public record Rule(
        // Name of the rule
        ClassName name,

        // Conditions
        Condition condition,

        // Actions
        Action action
) {
    public Rule(
            String obfuscatedPackageName
    ) {
        var ruleName = ClassName.get(obfuscatedPackageName, "Rule");
        var conditionName = ruleName.nestedClass("Condition");
        var actionName = ruleName.nestedClass("Action");

        this(
                ruleName,
                new Condition(conditionName),
                new Action(actionName)
        );
    }
}
