package bot.den.foxflow.builders.classes.rules;

import bot.den.foxflow.builders.names.Action;
import bot.den.foxflow.builders.TypedBuilder;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;

public class ActionBuilder implements TypedBuilder<TypeSpec> {
    private final Action action;
    private final TypeSpec.Builder builder;
    private final ClassName dataTypeName;
    private final ClassName commandName;

    public ActionBuilder(
            Action action,
            ClassName dataTypeName,
            ClassName commandName
    ) {
        this.action = action;
        this.dataTypeName = dataTypeName;
        this.commandName = commandName;

        builder = TypeSpec.interfaceBuilder(action.name())
                .addModifiers(Modifier.PUBLIC, Modifier.SEALED);

        addTransitionToRecord();
        addRunCommand();
        addFailLoudly();
    }

    private void addTransitionToRecord() {
        MethodSpec constructor = MethodSpec
                .constructorBuilder()
                .addParameter(dataTypeName, "data")
                .build();

        TypeSpec.Builder transitionTo = TypeSpec.recordBuilder(action.transitionTo())
                .addSuperinterface(action.name())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .recordConstructor(constructor);

        builder.addType(transitionTo.build());
    }

    private void addRunCommand() {
        MethodSpec constructor = MethodSpec
                .constructorBuilder()
                .addParameter(commandName, "command")
                .build();

        TypeSpec.Builder runCommand = TypeSpec.recordBuilder(action.runCommand())
                .addSuperinterface(action.name())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .recordConstructor(constructor);

        builder.addType(runCommand.build());
    }

    private void addFailLoudly() {
        TypeSpec.Builder runCommand = TypeSpec.recordBuilder(action.failLoudly())
                .addSuperinterface(action.name())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        builder.addType(runCommand.build());
    }

    @Override
    public TypeSpec build() {
        return builder.build();
    }
}
