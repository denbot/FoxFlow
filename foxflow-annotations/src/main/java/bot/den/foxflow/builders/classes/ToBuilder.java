package bot.den.foxflow.builders.classes;

import bot.den.foxflow.Generated;
import bot.den.foxflow.builders.HasClassName;
import bot.den.foxflow.builders.names.Names;
import bot.den.foxflow.builders.TypedBuilder;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import org.wpilib.units.Units;
import org.wpilib.units.measure.Time;

import javax.lang.model.element.Modifier;
import java.util.function.BooleanSupplier;

public class ToBuilder implements TypedBuilder<TypeSpec>, HasClassName {
    private final TypeSpec.Builder builder;
    private final Names names;

    public ToBuilder(
            Names names
    ) {
        this.names = names;
        builder = TypeSpec.classBuilder(names.toClassName())
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Generated.class)
                .superclass(names.limitedToClassName());

        addConstructor();
        addWhenMethod();
        addAlwaysMethod();
        addAfterTimeMethods();
    }

    private void addConstructor() {
        builder.addMethod(MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(names.managerClassName(), "manager")
                .addParameter(names.dataTypeName(), "fromState")
                .addParameter(names.dataTypeName(), "toState")
                .addStatement("super(manager, fromState, toState)")
                .build()
        );
    }

    private void addWhenMethod() {
        builder.addMethod(MethodSpec
                .methodBuilder("transitionWhen")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(BooleanSupplier.class, "booleanSupplier")
                .addCode("""
                        var rule = new $1T(new $2T(booleanSupplier), new $3T(toState));
                        this.manager.addRule(fromState, rule);
                        """,
                        names.rule().name(),
                        names.rule().condition().when(),
                        names.rule().action().transitionTo())
                .build()
        );
    }

    private void addAlwaysMethod() {
        builder.addMethod(MethodSpec
                .methodBuilder("transitionAlways")
                .addModifiers(Modifier.PUBLIC)
                .addCode("""
                        var rule = new $1T(new $2T(), new $3T(toState));
                        this.manager.addRule(fromState, rule);
                        """,
                        names.rule().name(),
                        names.rule().condition().always(),
                        names.rule().action().transitionTo())
                .build()
        );
    }

    private void addAfterTimeMethods() {
        builder.addMethod(MethodSpec
                .methodBuilder("transitionAfter")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(double.class, "seconds")
                .addStatement("transitionAfter($T.Seconds.of(seconds))", Units.class)
                .build()
        );

        builder.addMethod(MethodSpec
                .methodBuilder("transitionAfter")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Time.class, "time")
                .addCode("""
                        var rule = new $1T(new $2T(time), new $3T(toState));
                        this.manager.addRule(fromState, rule);
                        """,
                        names.rule().name(),
                        names.rule().condition().after(),
                        names.rule().action().transitionTo())
                .build()
        );
    }

    @Override
    public TypeSpec build() {
        return builder.build();
    }

    @Override
    public ClassName className() {
        return names.toClassName();
    }
}
