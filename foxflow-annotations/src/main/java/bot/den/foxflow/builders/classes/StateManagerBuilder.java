package bot.den.foxflow.builders.classes;

import bot.den.foxflow.Generated;
import bot.den.foxflow.builders.names.Names;
import bot.den.foxflow.builders.TypedBuilder;
import bot.den.foxflow.builders.features.CommandsV2;
import bot.den.foxflow.exceptions.DuplicateTimeSetup;
import bot.den.foxflow.exceptions.FailLoudlyException;
import bot.den.foxflow.exceptions.InvalidStateTransition;
import bot.den.foxflow.validator.EnumValidator;
import bot.den.foxflow.validator.RecordValidator;
import com.palantir.javapoet.*;
import org.wpilib.event.EventLoop;
import org.wpilib.system.Timer;
import org.wpilib.units.measure.Time;

import javax.lang.model.element.Modifier;
import java.util.*;

public class StateManagerBuilder implements TypedBuilder<TypeSpec> {
    private final TypeSpec.Builder builder;
    private final Names names;

    public StateManagerBuilder(
            Names names
    ) {
        this.names = names;
        builder = TypeSpec.classBuilder(names.managerClassName())
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Generated.class);

        addAddRuleMethod();
        addTriggerMethod();
        addUpdateStateMethod();
    }

    private void addAddRuleMethod() {
        builder.addMethod(MethodSpec
                .methodBuilder("addRule")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(names.dataTypeName(), "fromState")
                .addParameter(names.rule().name(), "rule")
                .addCode("""
                        if($1T.this.fromCache.enable(fromState)) {
                            $1T.this.currentSubData = $1T.this.fromCache.generate($1T.this.currentState);
                        }
                        
                        $1T.this.transitionRules.computeIfAbsent(fromState, _ -> new $2T<>());

                        if(rule.condition() instanceof $3T($6T time)) {
                            for(var otherRule : $1T.this.transitionRules.get(fromState)) {
                                if(otherRule.condition() instanceof $3T($6T otherTime)) {
                                    throw new $4T(
                                            fromState,
                                            time,
                                            otherTime
                                    );
                                }
                            }

                            $1T.this.timerMap.put(fromState, new $5T());
                        } else if(rule.condition() instanceof $7T($8T toState)) {
                            $1T.this.toCache.enable(toState);
                        }
                        
                        $1T.this.transitionRules.get(fromState).add(rule);
                        
                        if($1T.this.currentSubData.contains(fromState)) {
                            $1T.this.regenerateTransitionCaches();

                            if($1T.this.timerMap.containsKey(fromState)) {
                                $1T.this.timerMap.get(fromState).start();
                            }
                        }
                        """,
                        names.stateMachineClassName(),
                        ArrayList.class,
                        names.rule().condition().after(),
                        DuplicateTimeSetup.class,
                        Timer.class,
                        Time.class,
                        names.rule().condition().onTransition(),
                        names.dataTypeName())
                .build()
        );
    }

    private void addTriggerMethod() {
        if (this.names.commandsV2() instanceof CommandsV2 commandsV2) {
            builder.addMethod(MethodSpec
                    .methodBuilder("trigger")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(commandsV2.trigger())
                    .addParameter(EventLoop.class, "eventLoop")
                    .addParameter(names.dataTypeName(), "state")
                    .addCode("""
                                if($1T.this.fromCache.enable(state)) {
                                    $1T.this.currentSubData = $1T.this.fromCache.generate($1T.this.currentState);
                                }

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
                        "var $1LData = nextStateData.$1L();",
                        field.name()
                );
            }

            var code = CodeBlock.builder();

            code.add("$[var nextState = new $T(\n", rv.originalTypeName());

            var fields = rv.fields;
            for (int i = 0; i < fields.size(); i++) {
                var field = fields.get(i);
                var fieldName = field.name();
                var otherData = CodeBlock.of("$1LData", fieldName);

                if (rv.nestedInterfaces.containsKey(field.value())) {
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
                                var nextToStates = $1T.this.toCache.generate(nextState);
                                
                                if(! $2T.disjoint(runtimeCache.failLoudly, nextToStates)) {
                                    var failLoudly = new $3T("State transition was requested to fail loudly");
                                
                                    throw new $4T(currentState, nextState, failLoudly);
                                }
                                
                                var nextFromStates = $1T.this.fromCache.generate(nextState);
                                
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
                                
                                regenerateTransitionCaches();
                                """,
                        names.stateMachineClassName(),
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
