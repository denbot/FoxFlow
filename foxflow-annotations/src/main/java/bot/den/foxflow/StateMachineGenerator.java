package bot.den.foxflow;

import bot.den.foxflow.exceptions.AmbiguousTransitionSetup;
import bot.den.foxflow.exceptions.FailLoudlyException;
import bot.den.foxflow.exceptions.InvalidStateTransition;
import bot.den.foxflow.validator.EnumValidator;
import bot.den.foxflow.validator.RecordValidator;
import bot.den.foxflow.validator.Validator;
import com.palantir.javapoet.*;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StringPublisher;
import edu.wpi.first.units.Units;
import edu.wpi.first.units.measure.Time;
import edu.wpi.first.wpilibj.DSControlWord;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.event.EventLoop;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.button.Trigger;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public class StateMachineGenerator {
    final ProcessingEnvironment processingEnv;
    private final Environment environment;

    private final ClassName stateMachineClassName;
    private final ClassName stateManagerClassName;
    private final ClassName stateFromClassName;
    private final ClassName stateLimitedToClassName;
    private final ClassName stateToClassName;
    private final ClassName stateDataName;

    private final ClassName robotStateName;

    private final Validator validator;

    public StateMachineGenerator(Environment environment) {
        this.environment = environment;
        this.processingEnv = environment.processingEnvironment();
        var element = environment.element();

        if (element.getKind() == ElementKind.ENUM) {
            this.validator = new EnumValidator(environment);
            this.stateDataName = validator.originalTypeName();

        } else if (element.getKind() == ElementKind.RECORD) {
            this.validator = new RecordValidator(environment);
            this.stateDataName = validator.wrappedClassName();

        } else {
            throw new RuntimeException("The StateMachine annotation is only valid on enums and records");
        }

        ClassName annotatedClassName = (ClassName) ClassName.get(element.asType());
        String simpleStateName = annotatedClassName.simpleName();
        stateMachineClassName = annotatedClassName.peerClass(simpleStateName + "StateMachine");
        stateManagerClassName = stateMachineClassName.nestedClass(simpleStateName + "StateManager");
        stateFromClassName = annotatedClassName.peerClass(simpleStateName + "From");
        stateLimitedToClassName = annotatedClassName.peerClass(simpleStateName + "LimitedTo");
        stateToClassName = annotatedClassName.peerClass(simpleStateName + "To");

        robotStateName = ClassName.get(RobotState.class);
    }

    public void generate() {
        if (validator instanceof RecordValidator recordValidator) {
            for (var type : recordValidator.typesToWrite) {
                this.environment.writeType(type);
            }
        }

        TypeSpec internalStateManager = createInternalStateManager();
        generateLimitedToClass();
        generateToClass();
        generateFromClass();
        generateStateMachineClass(internalStateManager);
    }

    private TypeSpec createInternalStateManager() {
        MethodSpec whenMethod = MethodSpec
                .methodBuilder("transitionWhen")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(stateDataName, "fromState")
                .addParameter(stateDataName, "toState")
                .addParameter(BooleanSupplier.class, "booleanSupplier")
                .addCode("""
                                $1T.this.verifyFromStateEnabled(fromState);
                                
                                if(!$1T.this.transitionWhenMap.containsKey(fromState)) {
                                    $1T.this.transitionWhenMap.put(fromState, new $2T<>());
                                }
                                
                                var fromStateMap = $1T.this.transitionWhenMap.get(fromState);
                                
                                if(!fromStateMap.containsKey(toState)) {
                                    fromStateMap.put(toState, new $3T<>());
                                }
                                
                                fromStateMap.get(toState).add(booleanSupplier);
                                
                                if($1T.this.currentSubData.contains(fromState)) {
                                    $1T.this.regenerateTransitionWhenCache();
                                }
                                """,
                        stateMachineClassName,
                        HashMap.class,
                        ArrayList.class)
                .build();

        MethodSpec afterMethod = MethodSpec
                .methodBuilder("transitionAfter")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(stateDataName, "fromState")
                .addParameter(stateDataName, "toState")
                .addParameter(Time.class, "time")
                .addCode("""
                                $1T.this.verifyFromStateEnabled(fromState);
                                
                                if($1T.this.timeLimitMap.containsKey(fromState)) {
                                    throw new $4T(
                                            fromState,
                                            toState,
                                            $1T.this.timeLimitMap.get(fromState).$5L()
                                    );
                                }
                                
                                var timeRecord = new $2T(toState, time);
                                $1T.this.timeLimitMap.put(fromState, timeRecord);
                                $1T.this.timerMap.put(fromState, new $3T());
                                
                                if($1T.this.currentSubData.contains(fromState)) {
                                    $1T.this.regenerateTimerCache();
                                    $1T.this.timerMap.get(fromState).start();
                                }
                                """,
                        stateMachineClassName,
                        validator.timeClassName(),
                        Timer.class,
                        AmbiguousTransitionSetup.class,
                        validator instanceof EnumValidator ? "getFirst" : "data")
                .build();

        MethodSpec runMethod = MethodSpec
                .methodBuilder("run")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(stateDataName, "fromState")
                .addParameter(stateDataName, "toState")
                .addParameter(Command.class, "command")
                .addCode("""
                                $1T.this.verifyFromStateEnabled(fromState);
                                $1T.this.verifyToStateEnabled(toState);
                                
                                if(!$1T.this.transitionCommandMap.containsKey(fromState)) {
                                    $1T.this.transitionCommandMap.put(fromState, new $2T<>());
                                }
                                
                                var fromStateMap = $1T.this.transitionCommandMap.get(fromState);
                                if(!fromStateMap.containsKey(toState)) {
                                    fromStateMap.put(toState, new $3T<>());
                                }
                                
                                fromStateMap.get(toState).add(command);
                                
                                if($1T.this.currentSubData.contains(fromState)) {
                                    $1T.this.regenerateCommandCache();
                                }
                                """,
                        stateMachineClassName,
                        HashMap.class,
                        ArrayList.class)
                .build();

        MethodSpec failLoudlyMethod = MethodSpec
                .methodBuilder("failLoudly")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(stateDataName, "fromState")
                .addParameter(stateDataName, "toState")
                .addCode("""
                                $1T.this.verifyFromStateEnabled(fromState);
                                $1T.this.verifyToStateEnabled(toState);
                                
                                if(!$1T.this.failLoudlyMap.containsKey(fromState)) {
                                    $1T.this.failLoudlyMap.put(fromState, new $2T<>());
                                }
                                
                                $1T.this.failLoudlyMap.get(fromState).add(toState);
                                
                                if($1T.this.currentSubData.contains(fromState)) {
                                    $1T.this.regenerateFailLoudlyCache();
                                }
                                """,
                        stateMachineClassName,
                        HashSet.class)
                .build();

        MethodSpec triggerMethod = MethodSpec
                .methodBuilder("trigger")
                .addModifiers(Modifier.PUBLIC)
                .returns(Trigger.class)
                .addParameter(EventLoop.class, "eventLoop")
                .addParameter(stateDataName, "state")
                .addCode("""
                                $1T.this.verifyFromStateEnabled(state);
                                
                                if(! $1T.this.triggerMap.containsKey(state)) {
                                    var trigger = new Trigger(eventLoop, () -> $1T.this.currentSubData.contains(state));
                                    triggerMap.put(state, trigger);
                                }
                                
                                return triggerMap.get(state);
                                """,
                        stateMachineClassName
                )
                .build();

        return TypeSpec
                .classBuilder(stateManagerClassName)
                .addMethod(whenMethod)
                .addMethod(afterMethod)
                .addMethod(runMethod)
                .addMethod(failLoudlyMethod)
                .addMethod(triggerMethod)
                .build();
    }

    private void generateLimitedToClass() {
        FieldSpec managerField = FieldSpec
                .builder(stateManagerClassName, "manager")
                .addModifiers(Modifier.FINAL)
                .build();

        FieldSpec fromStateField = FieldSpec
                .builder(stateDataName, "fromState")
                .addModifiers(Modifier.FINAL)
                .build();

        FieldSpec toStateField = FieldSpec
                .builder(stateDataName, "toState")
                .addModifiers(Modifier.FINAL)
                .build();

        MethodSpec.Builder constructorBuilder = MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(stateManagerClassName, "manager")
                .addParameter(stateDataName, "fromState")
                .addParameter(stateDataName, "toState")
                .addCode("""
                        this.manager = manager;
                        this.fromState = fromState;
                        this.toState = toState;
                        """);

        if (validator.supportsStateTransition()) {
            constructorBuilder.addStatement("fromState.attemptTransitionTo(toState)");
        }

        MethodSpec constructor = constructorBuilder.build();

        MethodSpec runMethod = MethodSpec
                .methodBuilder("run")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Command.class, "command")
                .addStatement("this.manager.run(this.fromState, this.toState, command)")
                .build();

        MethodSpec failLoudlyMethod = MethodSpec
                .methodBuilder("failLoudly")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("this.manager.failLoudly(this.fromState, this.toState)")
                .build();

        TypeSpec type = TypeSpec
                .classBuilder(stateLimitedToClassName)
                .addModifiers(Modifier.PUBLIC)
                .addField(managerField)
                .addField(fromStateField)
                .addField(toStateField)
                .addMethod(constructor)
                .addMethod(runMethod)
                .addMethod(failLoudlyMethod)
                .build();

        this.environment.writeType(type);
    }

    private void generateToClass() {
        MethodSpec constructor = MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(stateManagerClassName, "manager")
                .addParameter(stateDataName, "fromState")
                .addParameter(stateDataName, "toState")
                .addStatement("super(manager, fromState, toState)")
                .build();

        MethodSpec whenMethod = MethodSpec
                .methodBuilder("transitionWhen")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(BooleanSupplier.class, "booleanSupplier")
                .returns(stateToClassName)
                .addStatement("this.manager.transitionWhen(this.fromState, this.toState, booleanSupplier)")
                .addStatement("return this")
                .build();

        MethodSpec alwaysMethod = MethodSpec
                .methodBuilder("transitionAlways")
                .addModifiers(Modifier.PUBLIC)
                .returns(stateToClassName)
                .addStatement("return transitionWhen(() -> true)")
                .build();

        MethodSpec afterDoubleMethod = MethodSpec
                .methodBuilder("transitionAfter")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(double.class, "seconds")
                .returns(stateToClassName)
                .addStatement("return transitionAfter($T.Seconds.of(seconds))", Units.class)
                .build();

        MethodSpec afterTimeMethod = MethodSpec
                .methodBuilder("transitionAfter")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Time.class, "time")
                .returns(stateToClassName)
                .addStatement("this.manager.transitionAfter(this.fromState, this.toState, time)")
                .addStatement("return this")
                .build();

        TypeSpec type = TypeSpec
                .classBuilder(stateToClassName)
                .addModifiers(Modifier.PUBLIC)
                .superclass(stateLimitedToClassName)
                .addMethod(constructor)
                .addMethod(whenMethod)
                .addMethod(alwaysMethod)
                .addMethod(afterDoubleMethod)
                .addMethod(afterTimeMethod)
                .build();

        this.environment.writeType(type);
    }

    private void generateFromClass() {
        FieldSpec managerField = FieldSpec
                .builder(stateManagerClassName, "manager")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build();

        FieldSpec targetStateField = FieldSpec
                .builder(stateDataName, "targetState")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build();

        MethodSpec constructor = MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(stateManagerClassName, "manager")
                .addParameter(stateDataName, "state")
                .addStatement("this.targetState = state")
                .addStatement("this.manager = manager")
                .build();

        List<MethodSpec> toMethods = validator.visitPermutations(new Validator.Visitor<>() {
            @Override
            public MethodSpec acceptUserDataType() {
                if (validator instanceof RecordValidator) {
                    // Inside the method doesn't call this, and we don't want the user to be able to in order to avoid RobotState issues
                    return null;
                }

                return MethodSpec
                        .methodBuilder("to")
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(validator.originalTypeName(), "state")
                        .returns(stateToClassName)
                        .addStatement("return new $T(this.manager, this.targetState, state)", stateToClassName)
                        .build();
            }

            @Override
            public MethodSpec acceptFields(RecordValidator validator, List<ClassName> fields) {
                var returnValue = fields.contains(robotStateName) ? stateLimitedToClassName : stateToClassName;
                var callingMethodName = returnValue.equals(stateToClassName) ? "to" : "limitedTo";

                MethodSpec.Builder methodBuilder = MethodSpec
                        .methodBuilder("to")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(returnValue);

                for (var type : fields) {
                    methodBuilder.addParameter(type, validator.fieldNameMap.get(type));
                }

                CodeBlock code = CodeBlock
                        .builder()
                        .add("return $L(\n", callingMethodName)
                        .add(
                                validator.dataEmitter(fields)
                                        .withConstructor()
                                        .withNestedClassesWrapped()
                                        .emit()
                        )
                        .add(");\n")
                        .build();

                return methodBuilder.addCode(code).build();
            }

            @Override
            public MethodSpec acceptWrapperDataType() {
                return MethodSpec
                        .methodBuilder("to")
                        .addModifiers(Modifier.PRIVATE)
                        .returns(stateToClassName)
                        .addParameter(validator.wrappedClassName(), "state")
                        .addStatement("return new $T(this.manager, this.targetState, state)", stateToClassName)
                        .build();
            }
        });

        if (validator instanceof RecordValidator rv && rv.robotStatePresent) {
            var method = MethodSpec
                    .methodBuilder("limitedTo")
                    .addModifiers(Modifier.PRIVATE)
                    .returns(stateLimitedToClassName)
                    .addParameter(stateDataName, "state")
                    .addStatement("return new $T(this.manager, this.targetState, state)", stateLimitedToClassName)
                    .build();

            toMethods.add(method);
        }


        MethodSpec triggerDefaultMethod = MethodSpec
                .methodBuilder("trigger")
                .addModifiers(Modifier.PUBLIC)
                .returns(Trigger.class)
                .addStatement("return this.trigger($T.getInstance().getDefaultButtonLoop())", CommandScheduler.class)
                .build();

        MethodSpec triggerEventLoopMethod = MethodSpec
                .methodBuilder("trigger")
                .addModifiers(Modifier.PUBLIC)
                .returns(Trigger.class)
                .addParameter(EventLoop.class, "eventLoop")
                .addStatement("return manager.trigger(eventLoop, targetState)")
                .build();

        TypeSpec.Builder typeBuilder = TypeSpec
                .classBuilder(stateFromClassName)
                .addModifiers(Modifier.PUBLIC)
                .addField(managerField)
                .addField(targetStateField)
                .addMethod(constructor);

        for (var toMethod : toMethods) {
            typeBuilder.addMethod(toMethod);
        }
        TypeSpec type = typeBuilder
                .addMethod(triggerDefaultMethod)
                .addMethod(triggerEventLoopMethod)
                .build();

        this.environment.writeType(type);
    }

    private void generateStateMachineClass(TypeSpec internalStateManager) {
        FieldSpec managerField = FieldSpec
                .builder(stateManagerClassName, "manager")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T()", stateManagerClassName)
                .build();

        FieldSpec networkTableInstance = FieldSpec
                .builder(NetworkTableInstance.class, "networkTableInstance")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("$T.getDefault()", NetworkTableInstance.class)
                .build();

        FieldSpec currentStateTopic = FieldSpec
                .builder(StringPublisher.class, "currentStateTopic")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("networkTableInstance.getStringTopic(\"StateMachine/currentState\").publish()")
                .build();

        FieldSpec currentStateField = FieldSpec
                .builder(validator.originalTypeName(), "currentState")
                .addModifiers(Modifier.PRIVATE)
                .build();

        var subDataSetType = ParameterizedTypeName.get(ClassName.get(Set.class), stateDataName);

        FieldSpec currentSubDataField = FieldSpec
                .builder(subDataSetType, "currentSubData")
                .addModifiers(Modifier.PRIVATE)
                .build();

        var transitionWhenMapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                stateDataName,
                ParameterizedTypeName.get(
                        ClassName.get(Map.class),
                        stateDataName,
                        ParameterizedTypeName.get(
                                List.class,
                                BooleanSupplier.class
                        )
                )
        );
        FieldSpec transitionWhenMap = FieldSpec
                .builder(transitionWhenMapType, "transitionWhenMap")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T()", HashMap.class)
                .build();

        var transitionWhenCacheType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ClassName.get(BooleanSupplier.class),
                ParameterizedTypeName.get(
                        ClassName.get(List.class),
                        validator.pairClassName()
                )
        );

        FieldSpec transitionWhenCache = FieldSpec
                .builder(transitionWhenCacheType, "transitionWhenCache")
                .addModifiers(Modifier.PRIVATE)
                .initializer("new $T()", HashMap.class)
                .build();

        ParameterizedTypeName commandListType = ParameterizedTypeName.get(
                List.class,
                Command.class
        );
        var transitionCommandMapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                stateDataName,
                ParameterizedTypeName.get(
                        ClassName.get(Map.class),
                        stateDataName,
                        commandListType
                )
        );

        var failLoudlyMapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                stateDataName,
                ParameterizedTypeName.get(
                        ClassName.get(Set.class),
                        stateDataName
                )
        );
        FieldSpec failLoudlyMap = FieldSpec
                .builder(failLoudlyMapType, "failLoudlyMap")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T()", HashMap.class)
                .build();

        var failLoudlyCacheType = ParameterizedTypeName.get(
                ClassName.get(Set.class),
                stateDataName
        );

        FieldSpec failLoudlyCache = FieldSpec
                .builder(failLoudlyCacheType, "failLoudlyCache")
                .addModifiers(Modifier.PRIVATE)
                .initializer("new $T()", HashSet.class)
                .build();

        FieldSpec transitionCommandMap = FieldSpec
                .builder(transitionCommandMapType, "transitionCommandMap")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T()", HashMap.class)
                .build();

        var transitionCommandCacheType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                stateDataName,
                commandListType
        );

        FieldSpec transitionCommandCache = FieldSpec
                .builder(transitionCommandCacheType, "transitionCommandCache")
                .addModifiers(Modifier.PRIVATE)
                .initializer("new $T()", HashMap.class)
                .build();

        var triggerMapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                stateDataName,
                ClassName.get(Trigger.class)
        );

        FieldSpec triggerMap = FieldSpec
                .builder(triggerMapType, "triggerMap")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T()", HashMap.class)
                .build();

        var timerMapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                stateDataName,
                ClassName.get(Timer.class)
        );

        FieldSpec timerMap = FieldSpec
                .builder(timerMapType, "timerMap")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T()", HashMap.class)
                .build();

        var timeLimitMapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                stateDataName,
                validator.timeClassName()
        );

        FieldSpec timeLimitMap = FieldSpec
                .builder(timeLimitMapType, "timeLimitMap")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T()", HashMap.class)
                .build();

        FieldSpec timerCache = FieldSpec
                .builder(Timer.class, "timerCache")
                .addModifiers(Modifier.PRIVATE)
                .build();

        FieldSpec timerFromStateCache = FieldSpec
                .builder(stateDataName, "timerFromStateCache")
                .addModifiers(Modifier.PRIVATE)
                .build();

        FieldSpec timeLimitCache = FieldSpec
                .builder(validator.timeClassName(), "timeLimitCache")
                .addModifiers(Modifier.PRIVATE)
                .build();

        final String FROM = "from";
        final String TO = "to";

        Map<String, Map<ClassName, FieldSpec>> innerClassEnabledFields = Map.ofEntries(
                Map.entry(FROM, new HashMap<>()),
                Map.entry(TO, new HashMap<>())
        );

        // Individual boolean enabled fields
        if (validator instanceof RecordValidator rv) {
            innerClassEnabledFields
                    .forEach((key, value) -> {
                        for (var innerClassName : rv.fieldToInnerClass.values()) {
                            var field = FieldSpec
                                    .builder(boolean.class, key + innerClassName.simpleName() + "Enabled")
                                    .addModifiers(Modifier.PRIVATE)
                                    .initializer("false")
                                    .build();
                            value.put(innerClassName, field);
                        }
                    });
        }

        FieldSpec controlWord = null;
        if (validator instanceof RecordValidator rv && rv.robotStatePresent) {
            controlWord = FieldSpec
                    .builder(ClassName.get(DSControlWord.class), "controlWord")
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                    .initializer("new $T()", DSControlWord.class)
                    .build();
        }

        List<MethodSpec> constructors = validator.visitTopLevel(new Validator.Visitor<>() {
            @Override
            public MethodSpec acceptUserDataType() {
                // We disallow using a record class in the constructor publicly just in case the record has a RobotState.
                var visibility = validator instanceof RecordValidator ? Modifier.PRIVATE : Modifier.PUBLIC;

                return MethodSpec
                        .constructorBuilder()
                        .addModifiers(visibility)
                        .addParameter(validator.originalTypeName(), "initialState")
                        .addCode("""
                                this.currentState = initialState;
                                this.currentSubData = this.generateToSubDataStates(initialState);
                                currentStateTopic.set(currentState.toString());
                                """)
                        .build();
            }

            @Override
            public MethodSpec acceptFields(RecordValidator validator, List<ClassName> fields) {
                MethodSpec.Builder constructorBuilder = MethodSpec
                        .constructorBuilder()
                        .addModifiers(Modifier.PUBLIC);

                for (var type : fields) {
                    // We don't want to allow the user to include the robotState in the constructor
                    if (type.equals(robotStateName)) {
                        continue;
                    }

                    constructorBuilder.addParameter(type, validator.fieldNameMap.get(type));
                }

                CodeBlock.Builder code = CodeBlock
                        .builder()
                        .add("this(new $T(", validator.originalTypeName())
                        .add(
                                validator
                                        .dataEmitter(fields)
                                        .withDefaultsSubstituted()
                                        .emit()
                        )
                        .add("));");

                constructorBuilder.addCode(code.build());

                return constructorBuilder.build();
            }

            @Override
            public MethodSpec acceptWrapperDataType() {
                // We don't need a state machine constructor that takes the data type we wrap
                return null;
            }
        });

        MethodSpec currentStateMethod = MethodSpec
                .methodBuilder("currentState")
                .addModifiers(Modifier.PUBLIC)
                .returns(validator.originalTypeName())
                .addStatement("return this.currentState")
                .build();

        List<MethodSpec> stateMethods = validator.visitPermutations(new Validator.Visitor<>() {
            @Override
            public MethodSpec acceptUserDataType() {
                CodeBlock dataParameter;
                if (validator instanceof EnumValidator) {
                    dataParameter = CodeBlock.of("state");
                } else if (validator instanceof RecordValidator rv) {
                    dataParameter = CodeBlock.of("$T.fromRecord(state)", rv.wrappedClassName());
                } else {
                    throw new RuntimeException("Unknown validator type");
                }

                return MethodSpec
                        .methodBuilder("state")
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(validator.originalTypeName(), "state")
                        .returns(stateFromClassName)
                        .addStatement("return new $T(this.manager, $L)", stateFromClassName, dataParameter)
                        .build();
            }

            @Override
            public MethodSpec acceptFields(RecordValidator validator, List<ClassName> fields) {

                MethodSpec.Builder methodBuilder = MethodSpec
                        .methodBuilder("state")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(stateFromClassName);

                for (var type : fields) {
                    methodBuilder.addParameter(type, validator.fieldNameMap.get(type));
                }

                CodeBlock code = CodeBlock
                        .builder()
                        .add("return state(")
                        .add(
                                validator.dataEmitter(fields)
                                        .withConstructor()
                                        .withNestedClassesWrapped()
                                        .emit()
                        )
                        .add(");")
                        .build();

                return methodBuilder.addCode(code).build();
            }

            @Override
            public MethodSpec acceptWrapperDataType() {
                // Internal use only for our wrapper method
                return MethodSpec
                        .methodBuilder("state")
                        .addModifiers(Modifier.PRIVATE)
                        .addParameter(validator.wrappedClassName(), "state")
                        .returns(stateFromClassName)
                        .addStatement("return new $T(this.manager, state)", stateFromClassName)
                        .build();
            }
        });

        List<MethodSpec> transitionToMethods = validator.visitPermutations(new Validator.Visitor<>() {
            @Override
            public MethodSpec acceptUserDataType() {
                if (validator instanceof EnumValidator) {
                    return MethodSpec
                            .methodBuilder("transitionTo")
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(validator.originalTypeName(), "state")
                            .returns(Command.class)
                            .addStatement("return $T.runOnce(() -> updateState(state)).ignoringDisable(true)", Commands.class)
                            .build();
                } else if (validator instanceof RecordValidator) {
                    /*
                    We don't make a transitionTo method for a record as the record could contain the RobotState and the
                    user should not be able to force that transition. We could theoretically check if the record had a
                    RobotState as one of its components, but then the method might "disappear" from the user's
                    perspective. We could ignore the robot state when updating our internal state, but that might be
                    confusing for the user who either expected that transition to hold or didn't know what value to put
                    for Robot State.
                     */
                    return null;
                } else {
                    throw new RuntimeException("Unknown validator type");
                }
            }

            @Override
            public MethodSpec acceptFields(RecordValidator validator, List<ClassName> fields) {
                if (fields.contains(robotStateName)) {
                    // We don't want to allow the user to ever transition to a specific RobotState
                    return null;
                }

                MethodSpec.Builder methodBuilder = MethodSpec
                        .methodBuilder("transitionTo")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(Command.class);

                for (var type : fields) {
                    methodBuilder.addParameter(type, validator.fieldNameMap.get(type));
                }

                CodeBlock code = CodeBlock
                        .builder()
                        .add("return transitionTo(")
                        .add(
                                validator.dataEmitter(fields)
                                        .withConstructor()
                                        .withNestedClassesWrapped()
                                        .emit()
                        )
                        .add(");")
                        .build();

                return methodBuilder.addCode(code).build();
            }

            @Override
            public MethodSpec acceptWrapperDataType() {
                return MethodSpec
                        .methodBuilder("transitionTo")
                        .addModifiers(Modifier.PRIVATE)
                        .addParameter(validator.wrappedClassName(), "state")
                        .returns(Command.class)
                        .addCode("""
                                        return $T.runOnce(() -> updateState(state)).ignoringDisable(true);
                                        """,
                                Commands.class)
                        .build();
            }
        });

        MethodSpec runPollCommandMethod = MethodSpec
                .methodBuilder("runPollCommand")
                .addModifiers(Modifier.PUBLIC)
                .returns(Command.class)
                .addCode("""
                                return $T.run(this::poll).ignoringDisable(true);
                                """,
                        Commands.class)
                .build();

        MethodSpec.Builder pollMethodBuilder = MethodSpec
                .methodBuilder("poll")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("$T nextState = this.getNextState()", stateDataName);

        if (validator instanceof RecordValidator rv && rv.robotStatePresent) {
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
                    stateDataName,
                    rv.fieldToInnerClass.get(List.of(robotStateName)));
        }

        pollMethodBuilder.addCode(
                """
                        if(nextState == null) {
                            return;
                        }
                        
                        this.updateState(nextState);
                        """);

        MethodSpec pollMethod = pollMethodBuilder.build();

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
                .returns(stateDataName)
                .addComment("Map of our input specifiers to list of valid outputs")
                .addCode("""
                                $1T possibleOptions = new $2T();
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
                                    $1T finalResults = new $2T();
                                    $4T seen = new $5T(possibleOptions);
                                    while(!possibleOptions.isEmpty()) {
                                        $1T mergedResults = new $2T();
                                    
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
                                    
                                    $1T bestOptions = new $2T();
                                    int bestNumElements = 0;
                                    for(var option : finalResults) {
                                        int ourNumElements = option.a().numElements();
                                        if(ourNumElements < bestNumElements) {
                                            continue;
                                        } else if(ourNumElements > bestNumElements) {
                                            bestOptions = new $2T();
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

        MethodSpec getNextStateMethod = getNextStateMethodBuilder.build();

        MethodSpec.Builder updateStateMethodBuilder = MethodSpec
                .methodBuilder("updateState")
                .addModifiers(Modifier.PRIVATE)
                .addParameter(stateDataName, "nextStateData");

        if (validator instanceof RecordValidator rv && rv.supportsStateTransition()) {
            updateStateMethodBuilder.addCode("""
                            var data = $1T.fromRecord(currentState);
                            data.attemptTransitionTo(nextStateData);
                            \n""",
                    stateDataName
            );
        } else if (validator instanceof EnumValidator ev && ev.supportsStateTransition()) {
            updateStateMethodBuilder.addStatement("currentState.attemptTransitionTo(nextStateData)");
        }

        // Create a new data instance or just assign the state manually depending on the type
        if (validator instanceof EnumValidator) {
            updateStateMethodBuilder
                    .addStatement("var nextState = nextStateData");
        } else if (validator instanceof RecordValidator rv) {
            for (var type : rv.fieldTypes) {
                var fieldName = rv.fieldNameMap.get(type);
                updateStateMethodBuilder.addStatement(
                        "var $1LData = $3T.get$2L(nextStateData)",
                        fieldName,
                        Util.ucfirst(fieldName),
                        rv.wrappedClassName()
                );
            }

            var code = CodeBlock.builder();

            code.add("$[var nextState = new $T(\n", rv.originalTypeName());

            List<ClassName> fieldTypes = rv.fieldTypes;
            for (int i = 0; i < fieldTypes.size(); i++) {
                var type = fieldTypes.get(i);
                var fieldName = rv.fieldNameMap.get(type);
                var otherData = CodeBlock.of("$1LData", fieldName);

                if (rv.nestedRecords.containsKey(type)) {
                    var dataType = rv.nestedRecords.get(type);
                    otherData = CodeBlock.of("$1T.toRecord($2LData)", dataType, fieldName);
                } else if (rv.nestedInterfaces.containsKey(type)) {
                    otherData = CodeBlock.of("$1LData.data()", fieldName);
                }

                code.add("$1LData == null ? currentState.$1L() : $2L", fieldName, otherData);
                if (i + 1 != fieldTypes.size()) {
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
                                currentStateTopic.set(currentState.toString());
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

        MethodSpec updateStateMethod = updateStateMethodBuilder.build();

        MethodSpec runTransitionCommands = MethodSpec
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
                .build();

        List<MethodSpec> verifyStateEnabledMethods = new ArrayList<>();
        innerClassEnabledFields
                .forEach((key, fieldMap) -> {
                    MethodSpec.Builder verifyStateEnabledMethodBuilder = MethodSpec
                            .methodBuilder("verify" + Util.ucfirst(key) + "StateEnabled")
                            .addModifiers(Modifier.PRIVATE)
                            .addParameter(stateDataName, "state");

                    if (validator instanceof EnumValidator) {
                        verifyStateEnabledMethodBuilder
                                .addComment("We have no states to enable, but this does make record state machine generation easier")
                                .addStatement("return");
                    } else if (validator instanceof RecordValidator) {
                        fieldMap
                                .entrySet()
                                .stream()
                                .sorted(Comparator.comparing(o -> o.getValue().name()))
                                .forEach((e) -> {
                                    verifyStateEnabledMethodBuilder.beginControlFlow("if(state instanceof $T)", e.getKey());

                                    String fieldName = e.getValue().name();
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
                });

        List<MethodSpec> generateSubDataStatesMethods = new ArrayList<>();
        innerClassEnabledFields
                .forEach((key, fieldMap) -> {
                    MethodSpec.Builder generateSubDataStateBuilder = MethodSpec
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

                        rv.fieldTypes.forEach(
                                (className) -> generateSubDataStateBuilder.addStatement("$1T $2LField = state.$2L()", className, rv.fieldNameMap.get(className))
                        );

                        fieldMap
                                .entrySet()
                                .stream()
                                .sorted(Comparator.comparing(o -> o.getValue().name()))
                                .forEach((e) -> {
                                    var innerClassName = e.getKey();
                                    var enabledField = e.getValue();

                                    generateSubDataStateBuilder
                                            .beginControlFlow("if(this.$L)", enabledField.name())
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
                });

        MethodSpec regenerateTransitionWhenCacheMethod = MethodSpec
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
                                                this.transitionWhenCache.put(supplier, new $2T());
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
                .build();

        MethodSpec regenerateCommandCacheMethod = MethodSpec
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
                .build();

        MethodSpec regenerateFailLoudlyCacheMethod = MethodSpec
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
                .build();

        CodeBlock timerCacheNumElementsLimiter;

        if (validator instanceof RecordValidator) {
            timerCacheNumElementsLimiter = CodeBlock.builder()
                    .addStatement("setCache = setCache || this.timeLimitCache.time().equals(timeLimit.time()) && this.timerFromStateCache.numElements() < timeLimit.data().numElements()")
                    .build();
        } else {
            timerCacheNumElementsLimiter = CodeBlock.builder().build();
        }

        MethodSpec regenerateTimerCache = MethodSpec
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
                .build();

        TypeSpec.Builder typeBuilder = TypeSpec
                .classBuilder(stateMachineClassName)
                .addModifiers(Modifier.PUBLIC)
                .addField(managerField)
                .addField(networkTableInstance)
                .addField(currentStateTopic)
                .addField(currentStateField)
                .addField(currentSubDataField)
                .addField(transitionWhenMap)
                .addField(transitionWhenCache)
                .addField(transitionCommandMap)
                .addField(transitionCommandCache)
                .addField(failLoudlyMap)
                .addField(failLoudlyCache)
                .addField(triggerMap)
                .addField(timerMap)
                .addField(timeLimitMap)
                .addField(timerCache)
                .addField(timerFromStateCache)
                .addField(timeLimitCache)
                .addMethod(regenerateTransitionWhenCacheMethod)
                .addMethod(regenerateCommandCacheMethod)
                .addMethod(regenerateFailLoudlyCacheMethod)
                .addMethod(regenerateTimerCache)
                .addMethod(runPollCommandMethod)
                .addMethod(pollMethod)
                .addMethod(getNextStateMethod)
                .addMethod(updateStateMethod)
                .addMethod(runTransitionCommands);

        if (controlWord != null) {
            typeBuilder.addField(controlWord);
        }

        // We want to add these boolean values to the state machine, but straight from the map they're out of order.
        innerClassEnabledFields
                .forEach((key, fieldMap) -> {
                    fieldMap
                            .values()
                            .stream()
                            .sorted(Comparator.comparing(FieldSpec::name))
                            .forEach(typeBuilder::addField);
                });

        for (var constructor : constructors) {
            typeBuilder.addMethod(constructor);
        }

        typeBuilder.addMethod(currentStateMethod);

        for (var stateMethod : stateMethods) {
            typeBuilder.addMethod(stateMethod);
        }

        for (var transitionToMethod : transitionToMethods) {
            typeBuilder.addMethod(transitionToMethod);
        }

        for (var verifyStateEnabledMethod : verifyStateEnabledMethods) {
            typeBuilder.addMethod(verifyStateEnabledMethod);
        }

        for (var generateSubDataStatesMethod : generateSubDataStatesMethods) {
            typeBuilder.addMethod(generateSubDataStatesMethod);
        }

        typeBuilder.addType(internalStateManager);

        this.environment.writeType(typeBuilder.build());
    }
}
