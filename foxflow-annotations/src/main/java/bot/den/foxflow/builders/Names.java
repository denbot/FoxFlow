package bot.den.foxflow.builders;

import bot.den.foxflow.RobotState;
import bot.den.foxflow.Util;
import bot.den.foxflow.builders.features.CommandsV2;
import bot.den.foxflow.validator.EnumValidator;
import bot.den.foxflow.validator.RecordValidator;
import bot.den.foxflow.validator.Validator;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;

import javax.lang.model.util.Elements;
import java.util.List;

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
        CommandsV2 commandsV2,

        // Related useful names we might use from features that are easier to propagate up in one place
        ClassName commandTypeName,
        ParameterizedTypeName commandListType
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

        ClassName commandType;
        if(hasCommandsV2) {
            commandType = commandsV2.command();
        } else {
            commandType = ClassName.get(Runnable.class);
        }
        var commandListType = ParameterizedTypeName.get(
                ClassName.get(List.class),
                commandType
        );

        this(
                validator,
                stateMachineClassName,
                stateMachineClassName.nestedClass("Manager"),
                ClassName.get(obfuscatedPackageName, "From"),
                ClassName.get(obfuscatedPackageName, "LimitedTo"),
                ClassName.get(obfuscatedPackageName, "To"),
                dataTypeName(validator),
                ClassName.get(RobotState.class),
                hasCommandsV2 ? commandsV2 : null,
                commandType,
                commandListType
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
