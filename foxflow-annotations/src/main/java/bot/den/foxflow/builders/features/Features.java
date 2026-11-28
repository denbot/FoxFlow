package bot.den.foxflow.builders.features;

import com.palantir.javapoet.ClassName;

import javax.lang.model.util.Elements;

public record Features(
        CommandsV2 commandsV2
) {
    public Features(Elements elements) {
        CommandsV2 commandsV2 = new CommandsV2();
        boolean hasCommandsV2 = elements.getTypeElement(commandsV2.command().canonicalName()) != null;

        this(
                hasCommandsV2 ? commandsV2 : null
        );
    }

    public ClassName commandType() {
        if (commandsV2 instanceof  CommandsV2 cv2) {
            return cv2.command();
        }

        return ClassName.get(Runnable.class);
    }
}
