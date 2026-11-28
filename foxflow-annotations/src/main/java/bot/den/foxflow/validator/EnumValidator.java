package bot.den.foxflow.validator;

import bot.den.foxflow.DefaultState;
import bot.den.foxflow.LimitsStateTransitions;
import bot.den.foxflow.Environment;
import bot.den.foxflow.builders.FieldHelper;
import bot.den.foxflow.exceptions.InvalidSetupException;
import com.palantir.javapoet.ClassName;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import java.util.Set;
import java.util.stream.Collectors;

public class EnumValidator implements Validator {
    private final ClassName originalTypeName;
    private final boolean implementsStateTransitionInterface;
    public final Element defaultOption;

    public EnumValidator(Environment environment) {
        var typeElement = environment.element();
        originalTypeName = ClassName.get(typeElement);

        implementsStateTransitionInterface = environment.validlySelfImplements(LimitsStateTransitions.class);

        Set<? extends Element> enumElements = environment.element()
                .getEnclosedElements()
                .stream()
                .filter(e -> e.getKind().equals(ElementKind.ENUM_CONSTANT))
                .collect(Collectors.toUnmodifiableSet());

        if(enumElements.isEmpty()) {
            throw new InvalidSetupException(typeElement + " must have at least one enum constant");
        }

        Set<? extends Element> defaultOptions = enumElements
                .stream()
                .filter(e -> e.getAnnotation(DefaultState.class) != null)
                .collect(Collectors.toUnmodifiableSet());

        if(defaultOptions.size() > 1) {
            throw new InvalidSetupException("Cannot put the @DefaultState annotation on more than one element of " + typeElement.getQualifiedName());
        }

        var first = defaultOptions.stream().findFirst();
        this.defaultOption = first.orElse(null);
    }

    @Override
    public ClassName originalTypeName() {
        return originalTypeName;
    }

    @Override
    public ClassName wrappedClassName() {
        throw new UnsupportedOperationException("Enum validator does not wrap the class name");
    }

    @Override
    public boolean supportsStateTransition() {
        return implementsStateTransitionInterface;
    }

    @Override
    public <R> FieldHelper<R> newFieldHelper() {
        return new FieldHelper<>();
    }
}
