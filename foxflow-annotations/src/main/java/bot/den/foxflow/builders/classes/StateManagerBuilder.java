package bot.den.foxflow.builders.classes;

import bot.den.foxflow.Util;
import bot.den.foxflow.builders.Names;
import bot.den.foxflow.builders.TypedBuilder;
import bot.den.foxflow.exceptions.AmbiguousTransitionSetup;
import bot.den.foxflow.exceptions.FailLoudlyException;
import bot.den.foxflow.exceptions.InvalidStateTransition;
import bot.den.foxflow.validator.EnumValidator;
import bot.den.foxflow.validator.RecordValidator;
import com.palantir.javapoet.*;
import edu.wpi.first.units.measure.Time;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.event.EventLoop;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.button.Trigger;

import javax.lang.model.element.Modifier;
import java.util.*;
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
        addUpdateStateMethod();
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

    private void addUpdateStateMethod() {
        var validator = names.validator();

        MethodSpec.Builder updateStateMethodBuilder = MethodSpec
                .methodBuilder("updateState")
                .addModifiers(Modifier.PUBLIC)
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
                                    if(!timerMap.containsKey(currentData)) {
                                        continue; // No timer to stop
                                    }

                                    if(nextFromStates.contains(currentData)) {
                                        continue; // This timer will continue on
                                    }

                                    var timer = timerMap.get(currentData);
                                    timer.stop();
                                    timer.reset();
                                }

                                // Start timers that aren't in our current state but are in our new
                                for(var nextData : nextFromStates) {
                                    if(!timerMap.containsKey(nextData)) {
                                        continue; // No timer to start
                                    }

                                    var timer = timerMap.get(nextData);
                                    timer.start(); // Start does nothing if the timer is already started
                                }

                                currentState = nextState;
                                currentStatePublisher.set(currentState.toString());
                                currentSubData = nextFromStates;

                                runTransitionCommands(nextToStates);

                                regenerateTransitionWhenCache();
                                regenerateCommandCache();
                                regenerateFailLoudlyCache();
                                regenerateTimerCache();
                                """,
                        Collections.class,
                        FailLoudlyException.class,
                        InvalidStateTransition.class);

        builder.addMethod(updateStateMethodBuilder.build());
    }

    @Override
    public TypeSpec build() {
        return builder.build();
    }
}
