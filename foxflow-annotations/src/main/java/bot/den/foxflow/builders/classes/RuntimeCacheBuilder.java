package bot.den.foxflow.builders.classes;

import bot.den.foxflow.builders.HasClassName;
import bot.den.foxflow.builders.TypedBuilder;
import bot.den.foxflow.builders.names.Names;
import bot.den.foxflow.validator.RecordValidator;
import bot.den.foxflow.validator.Validator;
import com.palantir.javapoet.*;
import org.wpilib.system.Timer;
import org.wpilib.units.measure.Time;

import javax.lang.model.element.Modifier;
import java.util.*;
import java.util.function.BooleanSupplier;

public class RuntimeCacheBuilder implements TypedBuilder<TypeSpec>, HasClassName {
    private final Names names;
    private final TypeSpec.Builder builder;

    public RuntimeCacheBuilder(Names names) {
        this.names = names;

        builder = TypeSpec.classBuilder(names.runtimeCacheName())
                .addModifiers(Modifier.PUBLIC);

        addConstructor();

        addTransitionWhenFields();
        addCommandFields();
        addFailLoudlyFields();
        addTimerFields();
    }

    private void addConstructor() {
        var subDataSetType = ParameterizedTypeName.get(
                ClassName.get(Set.class),
                names.dataTypeName()
        );

        var transitionRuleMapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                names.dataTypeName(),
                ParameterizedTypeName.get(
                        ClassName.get(List.class),
                        names.rule().name()
                )
        );

        CodeBlock timerCacheNumElementsLimiter;

        if (names.validator() instanceof RecordValidator) {
            timerCacheNumElementsLimiter = CodeBlock.builder()
                    .addStatement("setCache = setCache || this.timeLimit.time().equals(time) && this.timerFromState.numElements() < state.numElements()")
                    .build();
        } else {
            timerCacheNumElementsLimiter = CodeBlock.builder().build();
        }

        var constructor = MethodSpec
                .constructorBuilder()
                .addParameter(subDataSetType, "currentSubData")
                .addParameter(transitionRuleMapType, "transitionRules")
                .addModifiers(Modifier.PUBLIC)
                .addCode("""
                                currentSubData.forEach(state -> {
                                    if (!transitionRules.containsKey(state)) {
                                        return;
                                    }
                                
                                    for (var rule : transitionRules.get(state)) {
                                        switch(rule.action()) {
                                            case $4T($5T data) -> {
                                                switch(rule.condition()) {
                                                    case $6T _ -> {
                                                        $3T supplier = () -> true;
                                                        this.transitionWhen
                                                            .computeIfAbsent(supplier, _ -> new $1T<>())
                                                            .add(new $2T(state, data));
                                                    }
                                                    case $7T($3T supplier) -> {
                                                        this.transitionWhen
                                                            .computeIfAbsent(supplier, _ -> new $1T<>())
                                                            .add(new $2T(state, data));
                                                    }
                                                    case $8T($9T time) -> {
                                                        // If we have no time limit yet or this new time limit is shorter than our current one
                                                        boolean setCache = this.timeLimit == null || this.timeLimit.time().gt(time);
                                                        $11L
                                
                                                        if(setCache) {
                                                            this.timeLimit = new $10T(data, time);
                                                            this.timerFromState = state;
                                                        }
                                                    }
                                                    default -> throw new $13T("Cannot combine TransitionTo action with that condition");
                                                }
                                            }
                                            case $14T($15T command) -> {
                                                switch(rule.condition()) {
                                                    case $12T($5T toState) -> this.transitionCommand
                                                        .computeIfAbsent(toState, _ -> new $1T<>())
                                                        .add(command);
                                                    default -> throw new $13T("Cannot combine RunCommand action with that condition");
                                                }
                                            }
                                            case $16T _ -> {
                                                switch(rule.condition()) {
                                                    case $12T($5T toState) -> this.failLoudly.add(toState);
                                                    default -> throw new $13T("Cannot combine RunCommand action with that condition");
                                                }
                                            }
                                        }
                                    }
                                });
                                """,
                        ArrayList.class,
                        names.pairName(),
                        BooleanSupplier.class,
                        names.rule().action().transitionTo(),
                        names.dataTypeName(),
                        names.rule().condition().always(),
                        names.rule().condition().when(),
                        names.rule().condition().after(),
                        Time.class,
                        names.timeName(),
                        timerCacheNumElementsLimiter,
                        names.rule().condition().onTransition(),
                        UnsupportedOperationException.class,
                        names.rule().action().runCommand(),
                        names.commandTypeName(),
                        names.rule().action().failLoudly()
                )
                .build();

        builder.addMethod(constructor);
    }

    private void addTransitionWhenFields() {
        var transitionWhenCacheType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ClassName.get(BooleanSupplier.class),
                ParameterizedTypeName.get(
                        ClassName.get(List.class),
                        names.pairName()
                )
        );

        builder.addField(FieldSpec
                .builder(transitionWhenCacheType, "transitionWhen")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .initializer("new $T<>()", HashMap.class)
                .build()
        );
    }

    private void addCommandFields() {
        var transitionCommandCacheType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                names.dataTypeName(),
                names.commandListType()
        );

        builder.addField(FieldSpec
                .builder(transitionCommandCacheType, "transitionCommand")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .initializer("new $T<>()", HashMap.class)
                .build()
        );
    }

    private void addFailLoudlyFields() {
        var failLoudlyCacheType = ParameterizedTypeName.get(
                ClassName.get(Set.class),
                names.dataTypeName()
        );

        builder.addField(FieldSpec
                .builder(failLoudlyCacheType, "failLoudly")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .initializer("new $T<>()", HashSet.class)
                .build()
        );
    }

    private void addTimerFields() {
        builder.addField(FieldSpec
                .builder(names.dataTypeName(), "timerFromState")
                .addModifiers(Modifier.PUBLIC)
                .build()
        );

        builder.addField(FieldSpec
                .builder(names.timeName(), "timeLimit")
                .addModifiers(Modifier.PUBLIC)
                .build()
        );
    }

    @Override
    public TypeSpec build() {
        return builder.build();
    }

    @Override
    public ClassName className() {
        return names.runtimeCacheName();
    }
}
