package bot.den.foxflow.builders.classes.rules;

import bot.den.foxflow.builders.names.Condition;
import bot.den.foxflow.builders.TypedBuilder;
import bot.den.foxflow.builders.names.Names;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import org.wpilib.units.measure.Time;

import javax.lang.model.element.Modifier;
import java.util.function.BooleanSupplier;

public class ConditionBuilder implements TypedBuilder<TypeSpec> {
    private final Names names;
    private final Condition condition;
    private final TypeSpec.Builder builder;

    public ConditionBuilder(Names names) {
        this.names = names;
        this.condition = names.rule().condition();
        builder = TypeSpec.interfaceBuilder(condition.name())
                .addModifiers(Modifier.PUBLIC, Modifier.SEALED);

        addAlwaysRecord();
        addWhenRecord();
        addAfterRecord();
        addOnTransitionRecord();
    }

    private void addAlwaysRecord() {
        TypeSpec.Builder always = TypeSpec.recordBuilder(condition.always())
                .addSuperinterface(condition.name())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        builder.addType(always.build());
    }

    private void addWhenRecord() {
        MethodSpec constructor = MethodSpec
                .constructorBuilder()
                .addParameter(BooleanSupplier.class, "supplier")
                .build();

        TypeSpec.Builder when = TypeSpec.recordBuilder(condition.when())
                .addSuperinterface(condition.name())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .recordConstructor(constructor);

        builder.addType(when.build());
    }

    private void addAfterRecord() {
        MethodSpec constructor = MethodSpec
                .constructorBuilder()
                .addParameter(Time.class, "time")
                .build();

        TypeSpec.Builder when = TypeSpec.recordBuilder(condition.after())
                .addSuperinterface(condition.name())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .recordConstructor(constructor);

        builder.addType(when.build());
    }

    private void addOnTransitionRecord() {
        MethodSpec constructor = MethodSpec
                .constructorBuilder()
                .addParameter(names.dataTypeName(), "toState")
                .build();

        TypeSpec.Builder when = TypeSpec.recordBuilder(condition.onTransition())
                .addSuperinterface(condition.name())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .recordConstructor(constructor);

        builder.addType(when.build());
    }

    @Override
    public TypeSpec build() {
        return builder.build();
    }
}
