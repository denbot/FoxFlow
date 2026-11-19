package bot.den.foxflow.builders.classes.rules;

import bot.den.foxflow.Generated;
import bot.den.foxflow.builders.names.Names;
import bot.den.foxflow.builders.names.Rules;
import bot.den.foxflow.builders.TypedBuilder;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;

public class RulesBuilder implements TypedBuilder<TypeSpec> {
    private final Rules rules;
    private final TypeSpec.Builder builder;

    public RulesBuilder(Names names) {
        this.rules = names.rules();
        builder = TypeSpec.recordBuilder(rules.name())
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Generated.class);

        addConstructor();

        builder.addType(new ConditionBuilder(rules.condition()).build());
        builder.addType(new ActionBuilder(
                rules.action(),
                names.dataTypeName(),
                names.commandTypeName()
        ).build());
    }

    private void addConstructor() {
        MethodSpec constructor = MethodSpec
                .constructorBuilder()
                .addParameter(rules.condition().name(), "condition")
                .addParameter(rules.action().name(), "action")
                .build();

        builder.recordConstructor(constructor);
    }

    @Override
    public TypeSpec build() {
        return builder.build();
    }
}
