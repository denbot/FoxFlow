package bot.den.foxflow.builders.classes;

import bot.den.foxflow.Field;
import bot.den.foxflow.builders.Names;
import bot.den.foxflow.builders.TypedBuilder;
import bot.den.foxflow.builders.methods.TransitionToBuilder;
import bot.den.foxflow.builders.methods.TransitionToBuilder.TransitionToCode;
import bot.den.foxflow.exceptions.AmbiguousTransitionSetup;
import bot.den.foxflow.validator.EnumValidator;
import bot.den.foxflow.validator.RecordValidator;
import com.palantir.javapoet.*;
import edu.wpi.first.units.measure.Time;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.event.EventLoop;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.button.Trigger;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.BooleanSupplier;

public class StateManagerBuilder implements TypedBuilder<TypeSpec> {
    private final TypeSpec.Builder builder;
    private final Names names;

    public StateManagerBuilder(
            Names names
    ) {
        this.names = names;
        builder = TypeSpec.classBuilder(names.managerClassName())
                .addModifiers(Modifier.PUBLIC);

        addWhenMethod();
        addAfterMethod();
        addRunMethod();
        addFailLoudlyMethod();
        addTriggerMethod();
        addTransitionToMethods();
    }

    private void addWhenMethod() {
        builder.addMethod(MethodSpec
                .methodBuilder("transitionWhen")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(names.dataTypeName(), "fromState")
                .addParameter(names.dataTypeName(), "toState")
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
                        names.stateMachineClassName(),
                        HashMap.class,
                        ArrayList.class)
                .build()
        );
    }

    private void addAfterMethod() {
        builder.addMethod(MethodSpec
                .methodBuilder("transitionAfter")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(names.dataTypeName(), "fromState")
                .addParameter(names.dataTypeName(), "toState")
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
                        names.stateMachineClassName(),
                        names.validator().timeClassName(),
                        Timer.class,
                        AmbiguousTransitionSetup.class,
                        names.validator() instanceof EnumValidator ? "getFirst" : "data")
                .build()
        );
    }

    private void addRunMethod() {
        builder.addMethod(MethodSpec
                .methodBuilder("run")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(names.dataTypeName(), "fromState")
                .addParameter(names.dataTypeName(), "toState")
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
                        names.stateMachineClassName(),
                        HashMap.class,
                        ArrayList.class)
                .build()
        );
    }

    private void addFailLoudlyMethod() {
        builder.addMethod(MethodSpec
                .methodBuilder("failLoudly")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(names.dataTypeName(), "fromState")
                .addParameter(names.dataTypeName(), "toState")
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
                        names.stateMachineClassName(),
                        HashSet.class)
                .build()
        );
    }

    private void addTriggerMethod() {
        builder.addMethod(MethodSpec
                .methodBuilder("trigger")
                .addModifiers(Modifier.PUBLIC)
                .returns(Trigger.class)
                .addParameter(EventLoop.class, "eventLoop")
                .addParameter(names.dataTypeName(), "state")
                .addCode("""
                                $1T.this.verifyFromStateEnabled(state);
                                
                                if(! $1T.this.triggerMap.containsKey(state)) {
                                    var trigger = new Trigger(eventLoop, () -> $1T.this.currentSubData.contains(state));
                                    triggerMap.put(state, trigger);
                                }
                                
                                return triggerMap.get(state);
                                """,
                        names.stateMachineClassName()
                )
                .build()
        );
    }

    private void addTransitionToMethods() {
        var transitionToMethods = new TransitionToBuilder(names, new TransitionToCode() {
            @Override
            public CodeBlock enumCode() {
                return CodeBlock.of("return $T.this.transitionTo(state);", names.stateMachineClassName());
            }

            @Override
            public CodeBlock internalData() {
                return CodeBlock.of("");
            }

            @Override
            public CodeBlock fields(RecordValidator recordValidator, List<Field<ClassName>> fields) {
                return CodeBlock
                        .builder()
                        .add("return $T.this.transitionTo(", names.stateMachineClassName())
                        .add(
                                recordValidator.dataEmitter(fields)
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

        for(var method : transitionToMethods.build()) {
            builder.addMethod(method);
        }
    }

    @Override
    public TypeSpec build() {
        return builder.build();
    }
}
