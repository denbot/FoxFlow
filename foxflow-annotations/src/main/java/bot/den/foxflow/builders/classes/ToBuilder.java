package bot.den.foxflow.builders.classes;

import bot.den.foxflow.builders.Names;
import bot.den.foxflow.builders.TypedBuilder;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import edu.wpi.first.units.Units;
import edu.wpi.first.units.measure.Time;

import javax.lang.model.element.Modifier;
import java.util.function.BooleanSupplier;

public class ToBuilder implements TypedBuilder<TypeSpec> {
    private final TypeSpec.Builder builder;
    private final Names names;

    public ToBuilder(
            Names names
    ) {
        this.names = names;
        builder = TypeSpec.classBuilder(names.toClassName())
                .addModifiers(Modifier.PUBLIC)
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
                .returns(names.toClassName())
                .addStatement("this.manager.transitionWhen(this.fromState, this.toState, booleanSupplier)")
                .addStatement("return this")
                .build()
        );
    }

    private void addAlwaysMethod() {
        builder.addMethod(MethodSpec
                .methodBuilder("transitionAlways")
                .addModifiers(Modifier.PUBLIC)
                .returns(names.toClassName())
                .addStatement("return transitionWhen(() -> true)")
                .build()
        );
    }

    private void addAfterTimeMethods() {
        builder.addMethod(MethodSpec
                .methodBuilder("transitionAfter")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(double.class, "seconds")
                .returns(names.toClassName())
                .addStatement("return transitionAfter($T.Seconds.of(seconds))", Units.class)
                .build()
        );

        builder.addMethod(MethodSpec
                .methodBuilder("transitionAfter")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Time.class, "time")
                .returns(names.toClassName())
                .addStatement("this.manager.transitionAfter(this.fromState, this.toState, time)")
                .addStatement("return this")
                .build()
        );
    }

    @Override
    public TypeSpec build() {
        return builder.build();
    }
}
