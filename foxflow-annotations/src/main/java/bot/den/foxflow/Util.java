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
     * To hide implementation details on inner classes, we put them all in a sub-package of the original state class.
     * This ensures that an end-user of our State Machine can only reliably interact with the state machine, but not
     * with any other specific implementation.
     *
     * @param stateClass The record or field that the user is using for state
     * @return A unique obfuscated package name that the final user should not be able to guess
     */
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
