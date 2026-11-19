package bot.den.foxflow;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.TypeSpec;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public record Environment(
        ProcessingEnvironment processingEnvironment,
        RoundEnvironment roundEnvironment,
        TypeElement element
) {
    private static final Map<ClassName, String> uniquePackage = new HashMap<>();

    public Environment forNewElement(TypeElement element) {
        return new Environment(
                this.processingEnvironment,
                this.roundEnvironment,
                element
        );
    }

    public void writeType(String packageName, TypeSpec type) {
        JavaFile file = JavaFile.builder(packageName, type).indent("    ").build();
        try {
            file.writeTo(processingEnvironment.getFiler());
        } catch (IOException e) {
            error("Failed to write class " + packageName + "." + type.name());
            throw new RuntimeException(e);
        }
    }

    public void error(String error) {
        processingEnvironment.getMessager().printMessage(Diagnostic.Kind.ERROR, error, element);
    }

    public boolean validlySelfImplements(Class<?> clazz) {
        return validlySelfImplements(ClassName.get(clazz));
    }

    public boolean validlySelfImplements(ClassName interfaceName) {
        var typeUtils = this.processingEnvironment.getTypeUtils();

        var interfaces = element.getInterfaces();
        for (TypeMirror i : interfaces) {
            DeclaredType declaredType = (DeclaredType) i;
            TypeElement superClass = (TypeElement) typeUtils.asElement(declaredType);
            if (superClass.getKind() != ElementKind.INTERFACE) {
                continue;  // We only care about interfaces
            }

            if (!superClass.getQualifiedName().toString().equals(interfaceName.toString())) {
                continue;  // This isn't our interface
            }

            // We still need to check correctness
            var typeArguments = declaredType.getTypeArguments();
            if (typeArguments.size() != 1) {
                throw new RuntimeException("The " + interfaceName.simpleName() + " interface should only have one type argument");
            }

            String sameTypeErrorMessage = interfaceName.simpleName() + " parameter must be of type " + element;
            TypeMirror genericParameter = typeArguments.get(0);
            if (genericParameter.getKind() != TypeKind.DECLARED) {
                throw new RuntimeException(sameTypeErrorMessage);
            }

            TypeElement interfaceImplementation = (TypeElement) typeUtils.asElement(genericParameter);
            if (!interfaceImplementation.equals(element)) {
                throw new RuntimeException(sameTypeErrorMessage);
            }

            // At this point, that type is correctly implemented
            return true;
        }

        // We didn't implement the interface at all
        return false;
    }


    /**
     * To hide implementation details on inner classes, we put them all in a sub-package of the original state class.
     * This ensures that an end-user of our State Machine can only reliably interact with the state machine, but not
     * with any other specific implementation.
     *
     * @param stateClass The record or field that the user is using for state
     * @return A unique obfuscated package name that the final user should not be able to guess
     */
    public String getObfuscatedPackageName(ClassName stateClass) {
        if (uniquePackage.containsKey(stateClass)) {
            return uniquePackage.get(stateClass);
        }

        boolean useStableNames = processingEnvironment.getOptions()
                .containsKey(StateMachineAnnotationProcessor.stablePackageNamesOption);

        if(useStableNames) {
            String fullPackageName = stateClass.packageName() + ".ff" + HexFormat.of().toHexDigits(stateClass.hashCode());
            uniquePackage.put(stateClass, fullPackageName);
        } else {
            var random = new Random();

            while (true) {
                String randomString = random.ints(3, '0', '9')
                        .mapToObj(Character::toString)
                        .collect(Collectors.joining());

                String fullPackageName = stateClass.packageName() + ".ff" + randomString;

                if (uniquePackage.values().stream().noneMatch(v -> v.equals(fullPackageName))) {
                    // No collision, we can use this package name
                    uniquePackage.put(stateClass, fullPackageName);
                    break;
                }
            }
        }


        return uniquePackage.get(stateClass);
    }
}
