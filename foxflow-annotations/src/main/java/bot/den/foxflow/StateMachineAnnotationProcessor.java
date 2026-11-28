package bot.den.foxflow;

import bot.den.foxflow.exceptions.InvalidSetupException;
import bot.den.foxflow.exceptions.StateMachineProcessorException;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import java.util.Set;
import java.util.function.Consumer;

public class StateMachineAnnotationProcessor extends AbstractProcessor {
    public static final String stateMachineAnnotationClass = StateMachine.class.getTypeName();
    public static final String defaultStateAnnotationClass = DefaultState.class.getTypeName();
    public static final String stablePackageNamesOption = "foxflow.stablePackageNames";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        ElementFilter
                .typesIn(roundEnv.getElementsAnnotatedWith(StateMachine.class))
                .forEach((element -> {
                    var environment = new Environment(
                            processingEnv,
                            roundEnv,
                            element
                    );

                    try {
                        var generator = new StateMachineGenerator(environment);

                        generator.generate();
                    } catch (StateMachineProcessorException e) {
                        String message = e.getMessage();
                        if (message == null) {
                            message = e.getClass().toString();
                        }
                        environment.error(message);
                    }
                }
                ));

        roundEnv
                .getElementsAnnotatedWith(DefaultState.class)
                .forEach((element -> {
                    if (element.getKind() != ElementKind.ENUM_CONSTANT) {
                        processingEnv.getMessager().printMessage(
                                Diagnostic.Kind.ERROR,
                                "@DefaultState must only be used on enum constants",
                                element
                        );
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
