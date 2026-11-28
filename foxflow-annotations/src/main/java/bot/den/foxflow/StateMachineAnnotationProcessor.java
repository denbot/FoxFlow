package bot.den.foxflow;

import bot.den.foxflow.exceptions.StateMachineProcessorException;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Optional;
import java.util.Set;

public class StateMachineAnnotationProcessor extends AbstractProcessor {
    public static final String stateMachineAnnotationClass = StateMachine.class.getTypeName();
    public static final String defaultStateAnnotationClass = DefaultState.class.getTypeName();
    public static final String stablePackageNamesOption = "foxflow.stablePackageNames";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Optional<? extends TypeElement> annotationOptional =
                annotations.stream()
                        .filter((te) -> te.getQualifiedName().toString().equals(stateMachineAnnotationClass))
                        .findFirst();

        if (annotationOptional.isEmpty()) {
            return false;
        }

        TypeElement annotation = annotationOptional.get();
        roundEnv
                .getElementsAnnotatedWith(annotation)
                .forEach((element -> {
                    var environment = new Environment(
                            processingEnv,
                            roundEnv,
                            (TypeElement) element
                    );

                    try {
                        var generator = new StateMachineGenerator(environment);

                        generator.generate();
                    } catch (StateMachineProcessorException e) {
                        String message = e.getMessage();
                        if(message == null) {
                            message = e.getClass().toString();
                        }
                        environment.error(message);
                    }
                }));

        return true;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(stateMachineAnnotationClass, defaultStateAnnotationClass);
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of(stablePackageNamesOption);
    }
}
