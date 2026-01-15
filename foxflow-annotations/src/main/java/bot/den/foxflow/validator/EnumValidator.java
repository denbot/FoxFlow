package bot.den.foxflow.validator;

import bot.den.foxflow.DefaultState;
import bot.den.foxflow.LimitsStateTransitions;
import bot.den.foxflow.Environment;
import bot.den.foxflow.builders.Builder;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import edu.wpi.first.math.Pair;
import edu.wpi.first.units.measure.Time;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EnumValidator implements Validator {
    private final ClassName originalTypeName;
    private final boolean implementsStateTransitionInterface;
    private final Element defaultOption;

    public EnumValidator(Environment environment) {
        var typeElement = environment.element();
        originalTypeName = ClassName.get(typeElement);

        implementsStateTransitionInterface = environment.validlySelfImplements(LimitsStateTransitions.class);

        var defaultOptions = environment
                .roundEnvironment()
                .getElementsAnnotatedWith(DefaultState.class)
                .stream()
                .filter((e) -> e.getEnclosingElement().equals(environment.element()))
                .collect(Collectors.toUnmodifiableSet());

        if(defaultOptions.size() > 1) {
            throw new RuntimeException("Cannot put the @DefaultState annotation on more than one element of the same enum");
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
    public TypeName pairClassName() {
        return ParameterizedTypeName.get(
                ClassName.get(Pair.class),
                originalTypeName,
                originalTypeName
        );
    }

    @Override
    public TypeName timeClassName() {
        return ParameterizedTypeName.get(
                ClassName.get(Pair.class),
                originalTypeName,
                ClassName.get(Time.class)
        );
    }

    @Override
    public boolean supportsStateTransition() {
        return implementsStateTransitionInterface;
    }

    @Override
    public <R> Builder<R> newBuilder() {
        return new Builder<>();
    }
}
