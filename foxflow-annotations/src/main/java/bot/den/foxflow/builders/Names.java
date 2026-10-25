package bot.den.foxflow.builders;

import bot.den.foxflow.RobotState;
import bot.den.foxflow.Util;
import bot.den.foxflow.builders.features.CommandsV2;
import bot.den.foxflow.validator.EnumValidator;
import bot.den.foxflow.validator.RecordValidator;
import bot.den.foxflow.validator.Validator;
import com.palantir.javapoet.ClassName;

import javax.lang.model.util.Elements;

public record Names(
        Validator validator,

        // State Machine names
        ClassName stateMachineClassName,
        ClassName managerClassName,
        ClassName fromClassName,
        ClassName limitedToClassName,
        ClassName toClassName,
        ClassName dataTypeName,
        ClassName robotStateName,

        // Support for features
        CommandsV2 commandsV2
) {
    public Names(
            Elements elements,
            Validator validator,
            ClassName annotatedClassName
    ) {
        this(
                elements,
                validator,
                annotatedClassName.peerClass(annotatedClassName.simpleName() + "StateMachine"),
                Util.getObfuscatedPackageName(annotatedClassName)
        );
    }

    private Names(
            Elements elements,
            Validator validator,
            ClassName stateMachineClassName,
            String obfuscatedPackageName
    ) {
        CommandsV2 commandsV2 = new CommandsV2();
        boolean hasCommandsV2 = elements.getTypeElement(commandsV2.command().canonicalName()) != null;

        this(
                validator,
                stateMachineClassName,
                stateMachineClassName.nestedClass("Manager"),
                ClassName.get(obfuscatedPackageName, "From"),
                ClassName.get(obfuscatedPackageName, "LimitedTo"),
                ClassName.get(obfuscatedPackageName, "To"),
                dataTypeName(validator),
                ClassName.get(RobotState.class),
                hasCommandsV2 ? commandsV2 : null
        );
    }

    private static ClassName dataTypeName(Validator validator) {
        if (validator instanceof EnumValidator) {
            return validator.originalTypeName();
        } else if (validator instanceof RecordValidator) {
            return validator.wrappedClassName();
        } else {
            throw new RuntimeException("The StateMachine annotation is only valid on enums and records");
        }
    }
}
