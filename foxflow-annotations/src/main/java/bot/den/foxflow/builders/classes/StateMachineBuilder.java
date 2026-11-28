package bot.den.foxflow.builders.classes;

import bot.den.foxflow.DefaultState;
import bot.den.foxflow.Field;
import bot.den.foxflow.Generated;
import bot.den.foxflow.builders.FieldHelper;
import bot.den.foxflow.builders.HasClassName;
import bot.den.foxflow.builders.names.Names;
import bot.den.foxflow.builders.TypedBuilder;
import bot.den.foxflow.builders.features.CommandsV2;
import bot.den.foxflow.builders.methods.TransitionToBuilder;
import bot.den.foxflow.builders.methods.TransitionToBuilder.TransitionToCode;
import bot.den.foxflow.validator.EnumValidator;
import bot.den.foxflow.validator.RecordValidator;
import bot.den.foxflow.validator.Validator;
import com.palantir.javapoet.*;
import org.wpilib.driverstation.internal.DriverStationBackend;
import org.wpilib.hardware.hal.ControlWord;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.networktables.StringPublisher;
import org.wpilib.networktables.StringTopic;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StateMachineBuilder implements TypedBuilder<TypeSpec>, HasClassName {
    private final TypeSpec.Builder builder;
    private final Names names;
    private final Validator validator;

    private final ParameterizedTypeName subDataSetType;

    public StateMachineBuilder(
            Names names
    ) {
        this.names = names;
        this.validator = names.validator();
        builder = TypeSpec.classBuilder(names.stateMachineClassName())
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Generated.class);

        subDataSetType = ParameterizedTypeName.get(
                ClassName.get(Set.class),
                names.dataTypeName()
        );

        addManagerField();
        addCurrentDataFields();
        addEnabledCacheFields();
        addNetworkTablesFields();
        addTransitionRuleFields();
        addRuntimeCacheFields();
        addTriggerFields();
        addTimerMapField();
        addControlWordFields();

        addConstructors();

        addCurrentStateMethod();
        addStateMethods();
        addTransitionToMethods();
        addPollMethods();

        addGetNextStateMethod();

        addRunTransitionCommandsMethod();

        addRegenerateMethods();

        builder.addType(new StateManagerBuilder(names).build());
    }

    private void addTimerMapField() {
        var timerMapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                names.dataTypeName(),
                ClassName.get(org.wpilib.system.Timer.class)
        );

        builder.addField(FieldSpec
                .builder(timerMapType, "timerMap")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T<>()", HashMap.class)
                .build()
        );
    }

    private void addRuntimeCacheFields() {
        builder.addField(FieldSpec
                .builder(names.runtimeCacheName(), "runtimeCache")
                .addModifiers(Modifier.PRIVATE)
                .build()
        );
    }

    private void addManagerField() {
        builder.addField(FieldSpec
                .builder(names.managerClassName(), "manager")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T()", names.managerClassName())
                .build()
        );
    }

    private void addEnabledCacheFields() {
        builder.addField(FieldSpec
                .builder(names.enabledCacheName(), "fromCache")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T()", names.enabledCacheName())
                .build());


        builder.addField(FieldSpec
                .builder(names.enabledCacheName(), "toCache")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T()", names.enabledCacheName())
                .build());
    }

    private void addCurrentDataFields() {
        builder.addField(FieldSpec
                .builder(validator.originalTypeName(), "currentState")
                .addModifiers(Modifier.PRIVATE)
                .build()
        );

        builder.addField(FieldSpec
                .builder(subDataSetType, "currentSubData")
                .addModifiers(Modifier.PRIVATE)
                .build()
        );
    }

    private void addNetworkTablesFields() {
        builder.addField(FieldSpec
                .builder(NetworkTableInstance.class, "networkTableInstance")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("$T.getDefault()", NetworkTableInstance.class)
                .build()
        );

        builder.addField(FieldSpec
                .builder(StringTopic.class, "currentStateTopic")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .initializer("networkTableInstance.getStringTopic(\"FoxFlow/$1L/State\")", validator.originalTypeName().simpleName())
                .build()
        );

        builder.addField(FieldSpec
                .builder(StringPublisher.class, "currentStatePublisher")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("currentStateTopic.publish()")
                .build()
        );
    }

    private void addTransitionRuleFields() {
        var transitionRuleMapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                names.dataTypeName(),
                ParameterizedTypeName.get(
                        ClassName.get(List.class),
                        names.rule().name()
                )
        );

        builder.addField(FieldSpec
                .builder(transitionRuleMapType, "transitionRules")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T<>()", HashMap.class)
                .build()
        );
    }

    private void addTriggerFields() {
        if (this.names.features().commandsV2() instanceof CommandsV2 commandsV2) {
            var triggerMapType = ParameterizedTypeName.get(
                    ClassName.get(Map.class),
                    names.dataTypeName(),
                    commandsV2.trigger()
            );

            builder.addField(FieldSpec
                    .builder(triggerMapType, "triggerMap")
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                    .initializer("new $T<>()", HashMap.class)
                    .build()
            );
        }
    }

    private void addControlWordFields() {
        if (!(validator instanceof RecordValidator rv)) {
            return;
        }
        if (!rv.robotStatePresent) {
            return;
        }

        builder.addField(FieldSpec
                .builder(ClassName.get(ControlWord.class), "controlWord")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T()", ControlWord.class)
                .build()
        );
    }

    private void addConstructors() {
        FieldHelper<MethodSpec> constructors = validator.newFieldHelper();

        constructors.userDataType(() -> {
            // We disallow using a record class in the constructor publicly just in case the record has a RobotState.
            var visibility = validator instanceof RecordValidator ? Modifier.PRIVATE : Modifier.PUBLIC;

            return MethodSpec
                    .constructorBuilder()
                    .addModifiers(visibility)
                    .addParameter(validator.originalTypeName(), "initialState")
                    .addCode("""
                            this.currentState = initialState;
                            this.currentSubData = this.fromCache.generate(initialState);
                            this.runtimeCache = new $1T(currentSubData, transitionRules);
                            currentStatePublisher.set(currentState.toString());
                            """,
                            names.runtimeCacheName())
                    .build();
        });

        constructors
                .permuteFields(classNameField -> {
                    if (!(validator instanceof RecordValidator rv)) {
                        throw new UnsupportedOperationException("This method should not have been called with a non-record validator");
                    }

                    // No default value means it must be included in the constructor
                    if (!rv.defaultValues.containsKey(classNameField.value())) {
                        return List.of(classNameField);
                    }

                    Element defaultElement = rv.defaultValues.get(classNameField.value());
                    DefaultState annotation = defaultElement.getAnnotation(DefaultState.class);
                    if (annotation == null) {
                        throw new RuntimeException(classNameField.value() + " was in the default values but somehow wasn't annotated");
                    }

                    // It has a default value, but the user can't override it
                    if (!annotation.userCanOverride()) {
                        List<Field<ClassName>> result = new ArrayList<>();
                        result.add(null);
                        return result;
                    }

                    // It has a default value, and it can be overridden, so we allow both options
                    return Stream.of(null, classNameField).toList();
                })
                .fields((fields, className) -> {
                    if (!(validator instanceof RecordValidator rv)) {
                        throw new UnsupportedOperationException("This method should not have been called with a non-record validator");
                    }

                    MethodSpec.Builder constructorBuilder = MethodSpec
                            .constructorBuilder()
                            .addModifiers(Modifier.PUBLIC);

                    for (var field : fields) {
                        constructorBuilder.addParameter(field.value(), field.name());
                    }

                    var substitutedFieldTypes = rv.fields.stream().map(Field::value).collect(Collectors.toCollection(HashSet::new));
                    substitutedFieldTypes.removeAll(fields.stream().map(Field::value).collect(Collectors.toSet()));

                    CodeBlock.Builder code = CodeBlock
                            .builder()
                            .add("this(new $T(", validator.originalTypeName())
                            .add(
                                    rv
                                            .dataEmitter(rv.fields)
                                            .withDefaultsSubstituted(substitutedFieldTypes)
                                            .emit()
                            )
                            .add("));");

                    constructorBuilder.addCode(code.build());

                    return constructorBuilder.build();
                });

        for (var method : constructors) {
            builder.addMethod(method);
        }
    }

    private void addCurrentStateMethod() {
        builder.addMethod(MethodSpec
                .methodBuilder("currentState")
                .addModifiers(Modifier.PUBLIC)
                .returns(validator.originalTypeName())
                .addStatement("return this.currentState")
                .build()
        );
    }

    private void addStateMethods() {
        FieldHelper<MethodSpec> stateMethods = validator.newFieldHelper();

        stateMethods.userDataType(() -> {
            CodeBlock dataParameter;
            if (validator instanceof EnumValidator) {
                dataParameter = CodeBlock.of("state");
            } else if (validator instanceof RecordValidator) {
                dataParameter = CodeBlock.of("$T.fromRecord(state)", names.dataTypeName());
            } else {
                throw new RuntimeException("Unknown validator type");
            }

            return MethodSpec
                    .methodBuilder("state")
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(validator.originalTypeName(), "state")
                    .returns(names.fromClassName())
                    .addStatement("return new $T(this.manager, $L)", names.fromClassName(), dataParameter)
                    .build();
        });

        stateMethods.wrappedType(() -> {
            // Internal use only for our wrapper method
            return MethodSpec
                    .methodBuilder("state")
                    .addModifiers(Modifier.PRIVATE)
                    .addParameter(validator.wrappedClassName(), "state")
                    .returns(names.fromClassName())
                    .addStatement("return new $T(this.manager, state)", names.fromClassName())
                    .build();
        });

        stateMethods.permuteFields(FieldHelper.optional)
                .fields((fields, className) -> {
                    if (!(validator instanceof RecordValidator rv)) {
                        throw new UnsupportedOperationException("This method should not have been called with a non-record validator");
                    }

                    // Can't specify state on an empty list
                    if (className == null) {
                        return null;
                    }

                    MethodSpec.Builder methodBuilder = MethodSpec
                            .methodBuilder("state")
                            .addModifiers(Modifier.PUBLIC)
                            .returns(names.fromClassName());

                    for (var field : fields) {
                        methodBuilder.addParameter(field.value(), field.name());
                    }

                    CodeBlock code = CodeBlock
                            .builder()
                            .add("return state(")
                            .add(
                                    rv.dataEmitter(fields)
                                            .withConstructor()
                                            .withNestedClassesWrapped()
                                            .emit()
                            )
                            .add(");")
                            .build();

                    return methodBuilder.addCode(code).build();
                });

        for (var method : stateMethods) {
            builder.addMethod(method);
        }
    }

    private void addTransitionToMethods() {
        var transitionToBuilder = new TransitionToBuilder(names, new TransitionToCode() {
            @Override
            public CodeBlock enumCode() {
                return CodeBlock.of("manager.updateState(state);");
            }

            @Override
            public CodeBlock internalData() {
                return CodeBlock.of("");
            }

            @Override
            public CodeBlock fields(RecordValidator recordValidator, List<Field<ClassName>> fields) {
                return CodeBlock
                        .builder()
                        .add("manager.updateState(")
                        .add(
                                recordValidator.dataEmitter(fields)
                                        .withConstructor()
                                        .withNestedClassesWrapped()
                                        .emit()
                        )
                        .add(");")
                        .build();
            }

            @Override
            public TypeName returnType() {
                return TypeName.VOID;
            }
        });

        for (var method : transitionToBuilder.build()) {
            builder.addMethod(method);
        }
    }

    private void addPollMethods() {
        MethodSpec.Builder pollMethodBuilder = MethodSpec
                .methodBuilder("poll")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("$T nextState = this.getNextState()", names.dataTypeName());

        if (validator instanceof RecordValidator rv && rv.robotStatePresent) {
            var robotFieldOption = rv.fields.stream().filter(f -> f.value().equals(names.robotStateName())).findFirst();
            if (robotFieldOption.isEmpty()) {
                throw new RuntimeException("Robot state was supposedly present but we couldn't find the field");
            }

            pollMethodBuilder.addCode(
                    """
                            $3T.refreshControlWordFromCache(controlWord);
                            $1T nextRobotState = null;
                            if(currentState.robotState() != RobotState.DISABLED && !this.controlWord.isEnabled()) {
                                nextRobotState = new $2T(RobotState.DISABLED);
                            } else if(currentState.robotState() != RobotState.AUTO && this.controlWord.isAutonomousEnabled()) {
                                nextRobotState = new $2T(RobotState.AUTO);
                            } else if(currentState.robotState() != RobotState.TELEOP && this.controlWord.isTeleopEnabled()) {
                                nextRobotState = new $2T(RobotState.TELEOP);
                            } else if(currentState.robotState() != RobotState.TEST && this.controlWord.isUtility()) {
                                nextRobotState = new $2T(RobotState.TEST);
                            }
                            
                            if(nextState != null && nextRobotState != null) {
                                nextState = nextState.merge(nextRobotState);
                            } else if(nextState == null) {
                                nextState = nextRobotState;
                            }
                            """,
                    names.dataTypeName(),
                    rv.fieldToInnerClass.get(List.of(robotFieldOption.get())),
                    DriverStationBackend.class
            );
        }

        pollMethodBuilder.addCode(
                """
                        if(nextState == null) {
                            return;
                        }
                        
                        manager.updateState(nextState);
                        """);

        builder.addMethod(pollMethodBuilder.build());
    }

    private void addGetNextStateMethod() {
        var pairList = ParameterizedTypeName.get(
                ClassName.get(List.class),
                names.pairName()
        );
        var pairSet = ParameterizedTypeName.get(
                ClassName.get(Set.class),
                names.pairName()
        );

        MethodSpec.Builder getNextStateMethodBuilder = MethodSpec
                .methodBuilder("getNextState")
                .addModifiers(Modifier.PRIVATE)
                .returns(names.dataTypeName())
                .addComment("Map of our input specifiers to list of valid outputs")
                .addCode("""
                                $1T possibleOptions = new $2T<>();
                                for(var entry : this.runtimeCache.transitionWhen.entrySet()) {
                                    var supplier = entry.getKey();
                                
                                    if(supplier.getAsBoolean()) {
                                        possibleOptions.addAll(entry.getValue());
                                    }
                                }

                                var currentTimer = this.timerMap.get(this.runtimeCache.timerFromState);
                                if(currentTimer != null && currentTimer.hasElapsed(this.runtimeCache.timeLimit.time())) {
                                    possibleOptions.add(new $3T(this.runtimeCache.timerFromState, this.runtimeCache.timeLimit.data()));
                                }
                                
                                if(possibleOptions.isEmpty()) {
                                    return null;
                                } else if(possibleOptions.size() == 1) {
                                    return possibleOptions.get(0).b();
                                }
                                """,
                        pairList,
                        ArrayList.class,
                        names.pairName()
                );


        if (validator instanceof RecordValidator) {
            getNextStateMethodBuilder
                    .addCode("""
                                    $1T finalResults = new $2T<>();
                                    $4T seen = new $5T<>(possibleOptions);
                                    while(!possibleOptions.isEmpty()) {
                                        $1T mergedResults = new $2T<>();
                                    
                                        $3T option = possibleOptions.remove(0);
                                        boolean mergedThisOne = false;
                                        for(var other : possibleOptions) {
                                            if(option.equals(other)) {
                                                continue;
                                            }
                                            if(option.a().canMerge(other.a()) && option.b().canMerge(other.b())) {
                                                var newPair = new $3T(option.a().merge(other.a()), option.b().merge(other.b()));
                                                if(seen.contains(newPair)) {
                                                    continue;
                                                }
                                    
                                                mergedResults.add(newPair);
                                                mergedThisOne = true;
                                                seen.add(newPair);
                                            }
                                        }
                                    
                                        if(!mergedThisOne) {
                                            finalResults.add(option);
                                        }
                                    
                                        possibleOptions.addAll(mergedResults);
                                    }
                                    
                                    // Get the only item
                                    if(finalResults.size() == 1) {
                                        return finalResults.get(0).b();
                                    }
                                    
                                    $1T bestOptions = new $2T<>();
                                    int bestNumElements = 0;
                                    for(var option : finalResults) {
                                        int ourNumElements = option.a().numElements();
                                        if(ourNumElements < bestNumElements) {
                                            continue;
                                        } else if(ourNumElements > bestNumElements) {
                                            bestOptions = new $2T<>();
                                            bestNumElements = ourNumElements;
                                        }
                                        bestOptions.add(option);
                                    }
                                    
                                    return bestOptions.get(0).b();
                                    """,
                            pairList,
                            ArrayList.class,
                            names.pairName(),
                            pairSet,
                            HashSet.class
                    );
        } else {
            getNextStateMethodBuilder.addStatement("return null");
        }

        builder.addMethod(getNextStateMethodBuilder.build());
    }

    private void addRunTransitionCommandsMethod() {
        CodeBlock runCommands;
        if (this.names.features().commandsV2() instanceof CommandsV2 commandsV2) {
            runCommands = CodeBlock.of("""
                    for(var command : runtimeCache.transitionCommand.get(state)) {
                        $1T.getInstance().schedule(command);
                    }
                    """, commandsV2.commandScheduler());
        } else {
            runCommands = CodeBlock.of("""
                    for(var command : runtimeCache.transitionCommand.get(state)) {
                        command.run();
                    }
                    """);
        }

        builder.addMethod(MethodSpec
                .methodBuilder("runTransitionCommands")
                .addModifiers(Modifier.PRIVATE)
                .addParameter(subDataSetType, "nextStates")
                .addCode("""
                        nextStates.forEach(state -> {
                            if(! runtimeCache.transitionCommand.containsKey(state)) {
                                return;
                            }
                        
                            $1L
                        });
                        """, runCommands)
                .build()
        );
    }

    private void addRegenerateMethods() {
        builder.addMethod(MethodSpec
                .methodBuilder("regenerateTransitionCaches")
                .addModifiers(Modifier.PRIVATE)
                .addCode("""
                        this.runtimeCache = new $T(currentSubData, transitionRules);
                        """,
                        names.runtimeCacheName())
                .build()
        );
    }

    @Override
    public TypeSpec build() {
        return builder.build();
    }

    @Override
    public ClassName className() {
        return names.stateMachineClassName();
    }
}
