package bot.den.foxflow.builders;

import bot.den.foxflow.RobotState;
import bot.den.foxflow.Util;
import bot.den.foxflow.validator.EnumValidator;
import bot.den.foxflow.validator.RecordValidator;
import bot.den.foxflow.validator.Validator;
import com.palantir.javapoet.ClassName;

public record Names(
        Validator validator,
        ClassName stateMachineClassName,
        ClassName managerClassName,
        ClassName fromClassName,
        ClassName limitedToClassName,
        ClassName toClassName,
        ClassName dataTypeName,
        ClassName robotStateName
) {
    public Names(
            Validator validator,
            ClassName annotatedClassName
    ) {
        this(
                validator,
                annotatedClassName.peerClass(annotatedClassName.simpleName() + "StateMachine"),
                Util.getObfuscatedPackageName(annotatedClassName)
        );
    }

    private Names(
            Validator validator,
            ClassName stateMachineClassName,
            String obfuscatedPackageName
    ) {
        this(
                validator,
                stateMachineClassName,
                stateMachineClassName.nestedClass("Manager"),
                ClassName.get(obfuscatedPackageName, "From"),
                ClassName.get(obfuscatedPackageName, "LimitedTo"),
                ClassName.get(obfuscatedPackageName, "To"),
                dataTypeName(validator),
                ClassName.get(RobotState.class)
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
