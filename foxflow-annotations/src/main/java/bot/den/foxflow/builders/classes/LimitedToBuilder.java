package bot.den.foxflow.builders.classes;

import bot.den.foxflow.Field;
import bot.den.foxflow.Generated;
import bot.den.foxflow.builders.Names;
import bot.den.foxflow.builders.TypedBuilder;
import bot.den.foxflow.builders.methods.TransitionToBuilder;
import bot.den.foxflow.builders.methods.TransitionToBuilder.TransitionToCode;
import bot.den.foxflow.validator.RecordValidator;
import com.palantir.javapoet.*;
import edu.wpi.first.wpilibj2.command.Command;

import javax.lang.model.element.Modifier;
import java.util.List;

public class LimitedToBuilder implements TypedBuilder<TypeSpec> {
    private final TypeSpec.Builder builder;
    private final Names names;

    public LimitedToBuilder(
            Names names
    ) {
        this.names = names;
        builder = TypeSpec.classBuilder(names.limitedToClassName())
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Generated.class);

        addFields();
        addConstructor();
        addRunMethod();
        addFailLoudlyMethod();
        addTransitionToMethods();
    }

    private void addFields() {
        builder.addField(FieldSpec
                .builder(names.managerClassName(), "manager")
                .addModifiers(Modifier.FINAL)
                .build()
        );

        builder.addField(FieldSpec
                .builder(names.dataTypeName(), "fromState")
                .addModifiers(Modifier.FINAL)
                .build()
        );

        builder.addField(FieldSpec
                .builder(names.dataTypeName(), "toState")
                .addModifiers(Modifier.FINAL)
                .build()
        );
    }

    private void addConstructor() {
        MethodSpec.Builder constructorBuilder = MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(names.managerClassName(), "manager")
                .addParameter(names.dataTypeName(), "fromState")
                .addParameter(names.dataTypeName(), "toState")
                .addCode("""
                        this.manager = manager;
                        this.fromState = fromState;
                        this.toState = toState;
                        """);

        if (names.validator().supportsStateTransition()) {
            constructorBuilder.addStatement("fromState.attemptTransitionTo(toState)");
        }

        builder.addMethod(constructorBuilder.build());
    }

    private void addRunMethod() {
        builder.addMethod(MethodSpec
                .methodBuilder("run")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Command.class, "command")
                .addStatement("this.manager.run(this.fromState, this.toState, command)")
                .build()
        );
    }

    private void addFailLoudlyMethod() {
        builder.addMethod(MethodSpec
                .methodBuilder("failLoudly")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("this.manager.failLoudly(this.fromState, this.toState)")
                .build()
        );
    }

    private void addTransitionToMethods() {
        var transitionToBuilder = new TransitionToBuilder(names, new TransitionToCode() {
            @Override
            public CodeBlock enumCode() {
                return CodeBlock.of("this.run(this.manager.transitionTo(state));");
            }

            @Override
            public CodeBlock internalData() {
                return CodeBlock.of("");
            }

            @Override
            public CodeBlock fields(RecordValidator recordValidator, List<Field<ClassName>> fields) {
                return CodeBlock
                        .builder()
                        .add("this.run(this.manager.transitionTo(")
                        .add(
                                recordValidator.dataEmitter(fields)
                                        .emit()
                        )
                        .add("));")
                        .build();
            }

            @Override
            public TypeName returnType() {
                return TypeName.VOID;
            }
        });

        for(var method : transitionToBuilder.build()) {
            builder.addMethod(method);
        }
    }

    @Override
    public TypeSpec build() {
        return builder.build();
    }
}
