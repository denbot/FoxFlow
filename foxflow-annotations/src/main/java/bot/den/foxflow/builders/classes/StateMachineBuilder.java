package bot.den.foxflow.builders.classes;

import bot.den.foxflow.DefaultState;
import bot.den.foxflow.Field;
import bot.den.foxflow.Util;
import bot.den.foxflow.builders.FieldHelper;
import bot.den.foxflow.builders.Names;
import bot.den.foxflow.builders.TypedBuilder;
import bot.den.foxflow.builders.methods.TransitionToBuilder;
import bot.den.foxflow.builders.methods.TransitionToBuilder.TransitionToCode;
import bot.den.foxflow.exceptions.FailLoudlyException;
import bot.den.foxflow.exceptions.InvalidStateTransition;
import bot.den.foxflow.validator.EnumValidator;
import bot.den.foxflow.validator.RecordValidator;
import bot.den.foxflow.validator.Validator;
import com.palantir.javapoet.*;
import com.palantir.javapoet.MethodSpec.Builder;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StringPublisher;
import edu.wpi.first.networktables.StringTopic;
import edu.wpi.first.wpilibj.DSControlWord;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.button.Trigger;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StateMachineBuilder implements TypedBuilder<TypeSpec> {
    private final TypeSpec.Builder builder;
    private final Names names;
    private final Validator validator;

    private final String FROM = "from";

    private final Map<String, LinkedHashMap<ClassName, String>> innerClassEnabledFields;

    private final ParameterizedTypeName subDataSetType;
    private final ParameterizedTypeName commandListType;


    public StateMachineBuilder(
            Names names
    ) {
        this.names = names;
        this.validator = names.validator();
        builder = TypeSpec.classBuilder(names.stateMachineClassName())
                .addModifiers(Modifier.PUBLIC);

        subDataSetType = ParameterizedTypeName.get(
                ClassName.get(Set.class),
                names.dataTypeName()
        );
        commandListType = ParameterizedTypeName.get(
                List.class,
                Command.class
        );

        innerClassEnabledFields = Map.ofEntries(
                Map.entry(FROM, new LinkedHashMap<>()),
                Map.entry("to", new LinkedHashMap<>())
        );

        // Individual boolean enabled fields
        if (validator instanceof RecordValidator rv) {
            innerClassEnabledFields
                    .forEach((key, value) -> {
                        for (var innerClassName : rv.fieldToInnerClass.values()) {
                            value.put(innerClassName, key + innerClassName.simpleName() + "Enabled");
                        }
                    });
        }

        addManagerField();
        addCurrentDataFields();
        addNetworkTablesFields();
        addTransitionWhenFields();
        addCommandFields();
        addFailLoudlyFields();
        addTriggerFields();
        addTimerFields();
        addEnableFields();
        addControlWordFields();

        addConstructors();

        addCurrentStateMethod();
        addStateMethods();
        addTransitionToMethods();
        addPollMethods();

        addGetNextStateMethod();
        addUpdateStateMethod();

        addRunTransitionCommandsMethod();
        addVerifyStateEnabledCommands();

        addGenerateSubDataStatesMethods();
        addRegenerateMethods();

        builder.addType(new StateManagerBuilder(names).build());
    }

    private void addManagerField() {
        builder.addField(FieldSpec
                .builder(names.managerClassName(), "manager")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T()", names.managerClassName())
                .build()
        );
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

    private void addTransitionWhenFields() {
        var transitionWhenMapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                names.dataTypeName(),
                ParameterizedTypeName.get(
                        ClassName.get(Map.class),
                        names.dataTypeName(),
                        ParameterizedTypeName.get(
                                List.class,
                                BooleanSupplier.class
                        )
                )
        );

        builder.addField(FieldSpec
                .builder(transitionWhenMapType, "transitionWhenMap")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T<>()", HashMap.class)
                .build()
        );

        var transitionWhenCacheType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ClassName.get(BooleanSupplier.class),
                ParameterizedTypeName.get(
                        ClassName.get(List.class),
                        validator.pairClassName()
                )
        );

        builder.addField(FieldSpec
                .builder(transitionWhenCacheType, "transitionWhenCache")
                .addModifiers(Modifier.PRIVATE)
                .initializer("new $T<>()", HashMap.class)
                .build()
        );
    }

    private void addCommandFields() {
        var transitionCommandMapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                names.dataTypeName(),
                ParameterizedTypeName.get(
                        ClassName.get(Map.class),
                        names.dataTypeName(),
                        commandListType
                )
        );

        builder.addField(FieldSpec
                .builder(transitionCommandMapType, "transitionCommandMap")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T<>()", HashMap.class)
                .build()
        );

        var transitionCommandCacheType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                names.dataTypeName(),
                commandListType
        );

        builder.addField(FieldSpec
                .builder(transitionCommandCacheType, "transitionCommandCache")
                .addModifiers(Modifier.PRIVATE)
                .initializer("new $T<>()", HashMap.class)
                .build()
        );
    }

    private void addFailLoudlyFields() {
        var failLoudlyMapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                names.dataTypeName(),
                ParameterizedTypeName.get(
                        ClassName.get(Set.class),
                        names.dataTypeName()
                )
        );
        builder.addField(FieldSpec
                .builder(failLoudlyMapType, "failLoudlyMap")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T<>()", HashMap.class)
                .build()
        );

        var failLoudlyCacheType = ParameterizedTypeName.get(
                ClassName.get(Set.class),
                names.dataTypeName()
        );

        builder.addField(FieldSpec
                .builder(failLoudlyCacheType, "failLoudlyCache")
                .addModifiers(Modifier.PRIVATE)
                .initializer("new $T<>()", HashSet.class)
                .build()
        );
    }

    private void addTriggerFields() {
        var triggerMapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                names.dataTypeName(),
                ClassName.get(Trigger.class)
        );

        builder.addField(FieldSpec
                .builder(triggerMapType, "triggerMap")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T<>()", HashMap.class)
                .build()
        );
    }

    private void addTimerFields() {
        var timerMapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                names.dataTypeName(),
                ClassName.get(edu.wpi.first.wpilibj.Timer.class)
        );

        builder.addField(FieldSpec
                .builder(timerMapType, "timerMap")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T<>()", HashMap.class)
                .build()
        );

        var timeLimitMapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                names.dataTypeName(),
                validator.timeClassName()
        );

        builder.addField(FieldSpec
                .builder(timeLimitMapType, "timeLimitMap")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T<>()", HashMap.class)
                .build()
        );

        builder.addField(FieldSpec
                .builder(Timer.class, "timerCache")
                .addModifiers(Modifier.PRIVATE)
                .build()
        );

        builder.addField(FieldSpec
                .builder(names.dataTypeName(), "timerFromStateCache")
                .addModifiers(Modifier.PRIVATE)
                .build()
        );

        builder.addField(FieldSpec
                .builder(validator.timeClassName(), "timeLimitCache")
                .addModifiers(Modifier.PRIVATE)
                .build()
        );
    }

    private void addEnableFields() {
        for (var fieldMap : innerClassEnabledFields.values()) {
            for (var fieldName : fieldMap.values()) {
                builder.addField(FieldSpec
                        .builder(boolean.class, fieldName)
                        .addModifiers(Modifier.PRIVATE)
                        .initializer("false")
                        .build()
                );
            }
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
                .builder(ClassName.get(DSControlWord.class), "controlWord")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T()", DSControlWord.class)
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
                            this.currentSubData = this.generateToSubDataStates(initialState);
                            currentStatePublisher.set(currentState.toString());
                            """)
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
                return CodeBlock.of("return $T.runOnce(() -> updateState(state)).ignoringDisable(true);", Commands.class);
            }

            @Override
            public CodeBlock internalData() {
                return CodeBlock.of("return $T.runOnce(() -> updateState(state)).ignoringDisable(true);", Commands.class);
            }

            @Override
            public CodeBlock fields(RecordValidator recordValidator, List<Field<ClassName>> fields) {
                return CodeBlock
                        .builder()
                        .add("return transitionTo(")
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
                return ClassName.get(Command.class);
            }
        });

        for (var method : transitionToBuilder.build()) {
            builder.addMethod(method);
        }
    }

    private void addPollMethods() {
        builder.addMethod(MethodSpec
                .methodBuilder("runPollCommand")
                .addModifiers(Modifier.PUBLIC)
                .returns(Command.class)
                .addCode("""
                                return $T.run(this::poll).ignoringDisable(true);
                                """,
                        Commands.class)
                .build()
        );

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
                            this.controlWord.refresh();
                            $1T nextRobotState = null;
                            if(currentState.robotState() != RobotState.DISABLED && this.controlWord.isDisabled()) {
                                nextRobotState = new $2T(RobotState.DISABLED);
                            } else if(currentState.robotState() != RobotState.AUTO && this.controlWord.isAutonomousEnabled()) {
                                nextRobotState = new $2T(RobotState.AUTO);
                            } else if(currentState.robotState() != RobotState.TELEOP && this.controlWord.isTeleopEnabled()) {
                                nextRobotState = new $2T(RobotState.TELEOP);
                            } else if(currentState.robotState() != RobotState.TEST && this.controlWord.isTest()) {
                                nextRobotState = new $2T(RobotState.TEST);
                            }
                            
                            if(nextState != null && nextRobotState != null) {
                                nextState = nextState.merge(nextRobotState);
                            } else if(nextState == null) {
                                nextState = nextRobotState;
                            }
                            """,
                    names.dataTypeName(),
                    rv.fieldToInnerClass.get(List.of(robotFieldOption.get())));
        }

        pollMethodBuilder.addCode(
                """
                        if(nextState == null) {
                            return;
                        }
                        
                        this.updateState(nextState);
                        """);

        builder.addMethod(pollMethodBuilder.build());
    }

    private void addGetNextStateMethod() {
        var pairList = ParameterizedTypeName.get(
                ClassName.get(List.class),
                validator.pairClassName()
        );
        var pairSet = ParameterizedTypeName.get(
                ClassName.get(Set.class),
                validator.pairClassName()
        );

        MethodSpec.Builder getNextStateMethodBuilder = MethodSpec
                .methodBuilder("getNextState")
                .addModifiers(Modifier.PRIVATE)
                .returns(names.dataTypeName())
                .addComment("Map of our input specifiers to list of valid outputs")
                .addCode("""
                                $1T possibleOptions = new $2T<>();
                                for(var entry : this.transitionWhenCache.entrySet()) {
                                    var supplier = entry.getKey();
                                
                                    if(supplier.getAsBoolean()) {
                                        possibleOptions.addAll(entry.getValue());
                                    }
                                }
                                
                                if(this.timerCache != null && this.timerCache.hasElapsed(this.timeLimitCache.$5L())) {
                                    possibleOptions.add(new $4T(this.timerFromStateCache, this.timeLimitCache.$6L()));
                                }
                                
                                if(possibleOptions.isEmpty()) {
                                    return null;
                                } else if(possibleOptions.size() == 1) {
                                    return possibleOptions.get(0).$3L();
                                }
                                """,
                        pairList,
                        ArrayList.class,
                        validator instanceof EnumValidator ? "getSecond" : "b",
                        validator.pairClassName(),
                        validator instanceof EnumValidator ? "getSecond" : "time",
                        validator instanceof EnumValidator ? "getFirst" : "data"
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
                            validator.pairClassName(),
                            pairSet,
                            HashSet.class
                    );
        } else {
            getNextStateMethodBuilder.addStatement("return null");
        }

        builder.addMethod(getNextStateMethodBuilder.build());
    }

    private void addUpdateStateMethod() {
        MethodSpec.Builder updateStateMethodBuilder = MethodSpec
                .methodBuilder("updateState")
                .addModifiers(Modifier.PRIVATE)
                .addParameter(names.dataTypeName(), "nextStateData");

        if (validator instanceof RecordValidator rv && rv.supportsStateTransition()) {
            updateStateMethodBuilder.addCode("""
                            var data = $1T.fromRecord(currentState);
                            data.attemptTransitionTo(nextStateData);
                            \n""",
                    names.dataTypeName()
            );
        } else if (validator instanceof EnumValidator ev && ev.supportsStateTransition()) {
            updateStateMethodBuilder.addStatement("currentState.attemptTransitionTo(nextStateData)");
        }

        // Create a new data instance or just assign the state manually depending on the type
        if (validator instanceof EnumValidator) {
            updateStateMethodBuilder
                    .addStatement("var nextState = nextStateData");
        } else if (validator instanceof RecordValidator rv) {
            for (var field : rv.fields) {
                updateStateMethodBuilder.addStatement(
                        "var $1LData = $3T.get$2L(nextStateData)",
                        field.name(),
                        Util.ucfirst(field.name()),
                        rv.wrappedClassName()
                );
            }

            var code = CodeBlock.builder();

            code.add("$[var nextState = new $T(\n", rv.originalTypeName());

            var fields = rv.fields;
            for (int i = 0; i < fields.size(); i++) {
                var field = fields.get(i);
                var fieldName = field.name();
                var otherData = CodeBlock.of("$1LData", fieldName);

                if (rv.nestedRecords.containsKey(field.value())) {
                    var dataType = rv.nestedRecords.get(field.value());
                    otherData = CodeBlock.of("$1T.toRecord($2LData)", dataType, fieldName);
                } else if (rv.nestedInterfaces.containsKey(field.value())) {
                    otherData = CodeBlock.of("$1LData.data()", fieldName);
                }

                code.add("$1LData == null ? currentState.$1L() : $2L", fieldName, otherData);
                if (i + 1 != fields.size()) {
                    code.add(",");
                }
                code.add("\n");
            }

            code.add(");$]\n");

            updateStateMethodBuilder.addCode(code.build());
        }

        updateStateMethodBuilder
                .addCode("""
                                var nextToStates = generateToSubDataStates(nextState);
                                
                                if(! $1T.disjoint(failLoudlyCache, nextToStates)) {
                                    var failLoudly = new $2T("State transition was requested to fail loudly");
                                
                                    throw new $3T(currentState, nextState, failLoudly);
                                }
                                
                                var nextFromStates = generateFromSubDataStates(nextState);
                                
                                // Stop the current timers that aren't in our new state
                                for(var currentData : currentSubData) {
                                    if(!this.timerMap.containsKey(currentData)) {
                                        continue; // No timer to stop
                                    }
                                
                                    if(nextFromStates.contains(currentData)) {
                                        continue; // This timer will continue on
                                    }
                                
                                    var timer = this.timerMap.get(currentData);
                                    timer.stop();
                                    timer.reset();
                                }
                                
                                // Start timers that aren't in our current state but are in our new
                                for(var nextData : nextFromStates) {
                                    if(!this.timerMap.containsKey(nextData)) {
                                        continue; // No timer to start
                                    }
                                
                                    var timer = this.timerMap.get(nextData);
                                    timer.start(); // Start does nothing if the timer is already started
                                }
                                
                                this.currentState = nextState;
                                currentStatePublisher.set(currentState.toString());
                                this.currentSubData = nextFromStates;
                                
                                runTransitionCommands(nextToStates);
                                
                                this.regenerateTransitionWhenCache();
                                this.regenerateCommandCache();
                                this.regenerateFailLoudlyCache();
                                this.regenerateTimerCache();
                                """,
                        Collections.class,
                        FailLoudlyException.class,
                        InvalidStateTransition.class);

        builder.addMethod(updateStateMethodBuilder.build());
    }

    private void addRunTransitionCommandsMethod() {
        builder.addMethod(MethodSpec
                .methodBuilder("runTransitionCommands")
                .addModifiers(Modifier.PRIVATE)
                .addParameter(subDataSetType, "nextStates")
                .addCode("""
                        nextStates.forEach(state -> {
                            if(! transitionCommandCache.containsKey(state)) {
                                return;
                            }
                        
                            for(var command : transitionCommandCache.get(state)) {
                                $1T.getInstance().schedule(command);
                            }
                        });
                        """, CommandScheduler.class)
                .build()
        );
    }

    private void addVerifyStateEnabledCommands() {
        List<MethodSpec> verifyStateEnabledMethods = new ArrayList<>();

        for (var entry : innerClassEnabledFields.entrySet()) {
            String key = entry.getKey();
            var fieldMap = entry.getValue();
            Builder verifyStateEnabledMethodBuilder = MethodSpec
                    .methodBuilder("verify" + Util.ucfirst(key) + "StateEnabled")
                    .addModifiers(Modifier.PRIVATE)
                    .addParameter(names.dataTypeName(), "state");

            if (validator instanceof EnumValidator) {
                verifyStateEnabledMethodBuilder
                        .addComment("We have no states to enable, but this does make record state machine generation easier")
                        .addStatement("return");
            } else if (validator instanceof RecordValidator) {
                fieldMap
                        .entrySet()
                        .stream()
                        .sorted(Entry.comparingByValue())
                        .forEach((e) -> {
                            verifyStateEnabledMethodBuilder.beginControlFlow("if(state instanceof $T)", e.getKey());

                            String fieldName = e.getValue();
                            if (key.equals(FROM)) {
                                verifyStateEnabledMethodBuilder
                                        .beginControlFlow("if(!this.$L)", fieldName)
                                        .addStatement("this.$L = true", fieldName)
                                        .addStatement("this.currentSubData = this.generateFromSubDataStates(currentState)")
                                        .endControlFlow();
                            } else {
                                verifyStateEnabledMethodBuilder.addStatement("this.$L = true", fieldName);
                            }

                            verifyStateEnabledMethodBuilder
                                    .addStatement("return")
                                    .endControlFlow();
                        });
            }

            verifyStateEnabledMethods.add(verifyStateEnabledMethodBuilder.build());
        }

        for (var method : verifyStateEnabledMethods) {
            builder.addMethod(method);
        }
    }

    private void addGenerateSubDataStatesMethods() {
        List<MethodSpec> generateSubDataStatesMethods = new ArrayList<>();
        for (var entry : innerClassEnabledFields.entrySet()) {
            String key = entry.getKey();
            var fieldMap = entry.getValue();

            Builder generateSubDataStateBuilder = MethodSpec
                    .methodBuilder("generate" + Util.ucfirst(key) + "SubDataStates")
                    .addModifiers(Modifier.PRIVATE)
                    .addParameter(validator.originalTypeName(), "state")
                    .returns(subDataSetType);

            if (validator instanceof EnumValidator) {
                generateSubDataStateBuilder
                        .addComment("Enum state machines only ever contain the one state, this does make record state machine generation easier")
                        .addStatement("return Set.of(state)");
            } else if (validator instanceof RecordValidator rv) {
                generateSubDataStateBuilder
                        .addStatement("$1T result = new $2T<>()", subDataSetType, HashSet.class);

                rv.fields.forEach(
                        (field) -> generateSubDataStateBuilder.addStatement("$1T $2LField = state.$2L()", field.value(), field.name())
                );

                fieldMap
                        .entrySet()
                        .stream()
                        .sorted(Comparator.comparing(Entry::getValue))
                        .forEach((e) -> {
                            var innerClassName = e.getKey();
                            var fieldName = e.getValue();

                            generateSubDataStateBuilder
                                    .beginControlFlow("if(this.$L)", fieldName)
                                    .addStatement(
                                            "result.add($L)",
                                            rv.dataEmitter(innerClassName)
                                                    .withConstructor()
                                                    .withNestedClassesWrapped()
                                                    .withTransform(f -> f + "Field")
                                                    .emit()
                                    )
                                    .endControlFlow();
                        });

                generateSubDataStateBuilder
                        .addStatement("return result");
            }

            generateSubDataStatesMethods.add(generateSubDataStateBuilder.build());
        }

        for (var method : generateSubDataStatesMethods) {
            builder.addMethod(method);
        }
    }

    private void addRegenerateMethods() {
        builder.addMethod(MethodSpec
                .methodBuilder("regenerateTransitionWhenCache")
                .addModifiers(Modifier.PRIVATE)
                .addCode("""
                                this.transitionWhenCache = new $1T<>();
                                
                                this.currentSubData.forEach(state -> {
                                    if (!this.transitionWhenMap.containsKey(state)) {
                                        return;
                                    }
                                
                                    for (var fromEntry : this.transitionWhenMap.get(state).entrySet()) {
                                        for (var supplier : fromEntry.getValue()) {
                                            if (!this.transitionWhenCache.containsKey(supplier)) {
                                                this.transitionWhenCache.put(supplier, new $2T<>());
                                            }
                                
                                            this.transitionWhenCache.get(supplier).add(new $3T(state, fromEntry.getKey()));
                                        }
                                    }
                                });
                                """,
                        HashMap.class,
                        ArrayList.class,
                        validator.pairClassName()
                )
                .build()
        );

        builder.addMethod(MethodSpec
                .methodBuilder("regenerateCommandCache")
                .addModifiers(Modifier.PRIVATE)
                .addCode("""
                                this.transitionCommandCache = new $1T<>();
                                
                                this.currentSubData.forEach(state -> {
                                    if (!this.transitionCommandMap.containsKey(state)) {
                                        return;
                                    }
                                
                                    for(var entry : this.transitionCommandMap.get(state).entrySet()) {
                                        var toState = entry.getKey();
                                
                                        $2T commandList;
                                        if(this.transitionCommandCache.containsKey(toState)) {
                                            commandList = this.transitionCommandCache.get(toState);
                                        } else {
                                            commandList = new $3T<>();
                                            this.transitionCommandCache.put(toState, commandList);
                                        }
                                
                                        commandList.addAll(entry.getValue());
                                    }
                                });
                                """,
                        HashMap.class,
                        commandListType,
                        ArrayList.class)
                .build()
        );

        builder.addMethod(MethodSpec
                .methodBuilder("regenerateFailLoudlyCache")
                .addModifiers(Modifier.PRIVATE)
                .addCode("""
                                this.failLoudlyCache = new $1T<>();
                                
                                this.currentSubData.forEach(state -> {
                                    if (!this.failLoudlyMap.containsKey(state)) {
                                        return;
                                    }
                                
                                    this.failLoudlyCache.addAll(this.failLoudlyMap.get(state));
                                });
                                """,
                        HashSet.class)
                .build()
        );

        CodeBlock timerCacheNumElementsLimiter;

        if (validator instanceof RecordValidator) {
            timerCacheNumElementsLimiter = CodeBlock.builder()
                    .addStatement("setCache = setCache || this.timeLimitCache.time().equals(timeLimit.time()) && this.timerFromStateCache.numElements() < timeLimit.data().numElements()")
                    .build();
        } else {
            timerCacheNumElementsLimiter = CodeBlock.builder().build();
        }

        builder.addMethod(MethodSpec
                .methodBuilder("regenerateTimerCache")
                .addModifiers(Modifier.PRIVATE)
                .addCode("""
                                this.timerCache = null;
                                this.timeLimitCache = null;
                                this.timerFromStateCache = null;
                                
                                for(var subData : this.currentSubData) {
                                    if(! timerMap.containsKey(subData)) {
                                        continue;
                                    }
                                
                                    var timeLimit = timeLimitMap.get(subData);
                                
                                    // If we have no time limit yet or this new time limit is shorter than our current one
                                    boolean setCache = this.timeLimitCache == null || this.timeLimitCache.$1L().gt(timeLimit.$1L());
                                    $2L
                                    if(setCache) {
                                        this.timerCache = timerMap.get(subData);
                                        this.timeLimitCache = timeLimitMap.get(subData);
                                        this.timerFromStateCache = subData;
                                    }
                                }
                                """,
                        validator instanceof EnumValidator ? "getSecond" : "time",
                        timerCacheNumElementsLimiter
                )
                .build()
        );
    }

    @Override
    public TypeSpec build() {
        return builder.build();
    }
}
