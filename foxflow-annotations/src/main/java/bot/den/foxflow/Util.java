package bot.den.foxflow;

import com.palantir.javapoet.ClassName;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class Util {
    private static final Map<ClassName, Integer> uniqueNameCounter = new HashMap<>();
    private static final Map<ClassName, String> uniquePackage = new HashMap<>();

    /**
     * @param input The input to adjust
     * @return The input with the first letter uppercase
     */
    public static String ucfirst(String input) {
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    /**
     * It's necessary to eliminate the wrapping classes to simplify things significantly in other areas. The caveat is
     * that we must also create a unique name. While it's unlikely to cause a conflict under typical circumstances, I
     * thought it wise to avoid potential clashes, as they are technically possible. (And our unit tests are set up to
     * fail if we don't)
     *
     * @param startingClassName What name would the caller prefer to go with, if available
     * @return The name you're going to have to settle for
     */
    public static ClassName getUniqueClassName(ClassName startingClassName) {
        var baseClass = startingClassName;
        while (baseClass.enclosingClassName() != null) {
            baseClass = baseClass.enclosingClassName();
        }

        ClassName uniqueDataClassName;
        String specifier = "";
        int counter = 0;
        while (true) {
            uniqueDataClassName = baseClass.peerClass(startingClassName.simpleName() + specifier);

            if (!uniqueNameCounter.containsKey(uniqueDataClassName)) {
                // Congrats, your name is unique
                uniqueNameCounter.put(uniqueDataClassName, counter);
                return uniqueDataClassName;
            }

            counter++;
            specifier = String.valueOf(counter);
        }
    }

    public static String getObfuscatedPackageName(ClassName stateClass) {
        if (uniquePackage.containsKey(stateClass)) {
            return uniquePackage.get(stateClass);
        }

        var random = new Random();

        while(true) {
            String randomString = random.ints(3, '0', '9')
                    .mapToObj(Character::toString)
                    .collect(Collectors.joining());

            String fullPackageName = stateClass.packageName() + ".ff" + randomString;

            if(uniquePackage.values().stream().noneMatch(v -> v.equals(fullPackageName))) {
                // No collision, we can use this package name
                uniquePackage.put(stateClass, fullPackageName);
                break;
            }
        }


        return uniquePackage.get(stateClass);
    }
}
