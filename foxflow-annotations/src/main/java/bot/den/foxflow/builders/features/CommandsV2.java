package bot.den.foxflow.builders.features;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;

import java.util.List;

public record CommandsV2(
        ClassName command,
        ClassName commands,
        ClassName commandScheduler,
        ClassName trigger
) {
    public CommandsV2() {
        String namespace = "org.wpilib.command2";
        ClassName commandType = ClassName.get(namespace, "Command");

        this(
                commandType,
                ClassName.get(namespace, "Commands"),
                ClassName.get(namespace, "CommandScheduler"),
                ClassName.get(namespace + ".button", "Trigger")
        );
    }
}
