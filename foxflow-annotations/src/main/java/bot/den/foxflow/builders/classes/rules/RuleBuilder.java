package bot.den.foxflow.builders.classes.rules;

import bot.den.foxflow.Generated;
import bot.den.foxflow.builders.names.Names;
import bot.den.foxflow.builders.names.Rule;
import bot.den.foxflow.builders.TypedBuilder;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;

public class RuleBuilder implements TypedBuilder<TypeSpec> {
    private final Rule rule;
    private final TypeSpec.Builder builder;

    public RuleBuilder(Names names) {
        this.rule = names.rule();
        builder = TypeSpec.recordBuilder(rule.name())
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Generated.class);

        addConstructor();

        builder.addType(new ConditionBuilder(rule.condition()).build());
        builder.addType(new ActionBuilder(
                rule.action(),
                names.dataTypeName(),
                names.commandTypeName()
        ).build());
    }

    private void addConstructor() {
        MethodSpec constructor = MethodSpec
                .constructorBuilder()
                .addParameter(rule.condition().name(), "condition")
                .addParameter(rule.action().name(), "action")
                .build();

        builder.recordConstructor(constructor);
    }

    @Override
    public TypeSpec build() {
        return builder.build();
    }
}
