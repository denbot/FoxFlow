package bot.den.foxflow.builders;

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
}
