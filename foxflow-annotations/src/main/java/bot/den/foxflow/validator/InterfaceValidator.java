package bot.den.foxflow.validator;

import bot.den.foxflow.LimitsStateTransitions;
import bot.den.foxflow.Environment;
import bot.den.foxflow.LimitsTypeTransitions;
import bot.den.foxflow.Util;
import bot.den.foxflow.builders.Builder;
import com.palantir.javapoet.*;
import edu.wpi.first.math.Pair;
import edu.wpi.first.units.measure.Time;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;

public class InterfaceValidator implements Validator {
    public final List<Pair<String, TypeSpec>> typesToWrite = new ArrayList<>();
    private final ClassName originalTypeName;
    private final ClassName wrappedTypeName;
    private final ClassName pairName;
    private final ClassName timeName;

    public InterfaceValidator(Environment environment) {
        originalTypeName = ClassName.get(environment.element());
        String obfuscatedPackageName = Util.getObfuscatedPackageName(originalTypeName);
        wrappedTypeName = ClassName.get(obfuscatedPackageName, "Data");
        pairName = wrappedTypeName.nestedClass("Pair");
        timeName = wrappedTypeName.nestedClass("Time");

        typesToWrite.add(new Pair<>(obfuscatedPackageName, createRecordWrapper()));
    }

    @Override
    public ClassName originalTypeName() {
        return originalTypeName;
    }

    @Override
    public ClassName wrappedClassName() {
        return wrappedTypeName;
    }

    @Override
    public ClassName pairClassName() {
        return pairName;
    }

    @Override
    public TypeName timeClassName() {
        return timeName;
    }

    @Override
    public boolean supportsStateTransition() {
        // All interface classes support transition because we create a new data wrapper
        return true;
    }

    @Override
    public <R> Builder<R> newBuilder() {
        throw new UnsupportedOperationException("Not currently supported on interfaces");
    }

    private TypeSpec createRecordWrapper() {
        ParameterizedTypeName limitsStateTransitions = ParameterizedTypeName
                .get(
                        ClassName.get(LimitsStateTransitions.class),
                        wrappedTypeName
                );

        MethodSpec constructor = MethodSpec
                .constructorBuilder()
                .addParameter(originalTypeName, "data")
                .build();

        MethodSpec fromRecord = MethodSpec
                .methodBuilder("fromRecord")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(originalTypeName, "data")
                .returns(wrappedTypeName)
                .addStatement("return new $1T(data)", wrappedTypeName)
                .build();

        MethodSpec canTransitionState = MethodSpec
                .methodBuilder("canTransitionState")
                .addAnnotation(Override.class)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "\"unchecked\"").build())
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addParameter(wrappedTypeName, "data")
                .addCode("""
                                Class ourClass = this.data.getClass();
                                Class theirClass = data.data.getClass();
                                if(ourClass.equals(theirClass) && this.data instanceof $1T ourData) {
                                    // otherData's class is ourData's class by the check above, but the compiler doesn't understand that, hence
                                    // the SuppressWarnings annotation above.
                                    return ourData.canTransitionState(data.data);
                                }
                                if(! ourClass.equals(theirClass) && this.data instanceof $2T ourData) {
                                    return ourData.canTransitionType(data.data);
                                }
                                return true;
                                """,
                        LimitsStateTransitions.class,
                        LimitsTypeTransitions.class)
                .build();

        return TypeSpec
                .recordBuilder(wrappedTypeName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(limitsStateTransitions)
                .recordConstructor(constructor)
                .addMethod(fromRecord)
                .addMethod(canTransitionState)
                .addType(createPair())
                .addType(createTime())
                .build();
    }

    private TypeSpec createPair() {
        MethodSpec dataConstructor = MethodSpec
                .constructorBuilder()
                .addParameter(wrappedTypeName, "a")
                .addParameter(wrappedTypeName, "b")
                .build();

        return TypeSpec
                .recordBuilder(pairName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .recordConstructor(dataConstructor)
                .build();
    }

    private TypeSpec createTime() {
        MethodSpec dataConstructor = MethodSpec
                .constructorBuilder()
                .addParameter(wrappedTypeName, "data")
                .addParameter(Time.class, "time")
                .build();

        return TypeSpec
                .recordBuilder(timeName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .recordConstructor(dataConstructor)
                .build();
    }
}
