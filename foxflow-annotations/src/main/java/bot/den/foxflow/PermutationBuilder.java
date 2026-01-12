package bot.den.foxflow;

import java.util.*;


/**
 * This class finds all permutations of a given input and maintains the order of the input types in its output.
 * E.g., ["A", "B", "C"] as input would give you these permutations:
 * ["A"], ["B"], ["C"]
 * ["A", "B"], ["B", "C"]
 * ["A", "B", "C"]
 */
public class PermutationBuilder<T> {
    private final List<T> inputs;
    private final Map<List<T>, Set<List<T>>> permutationMap = new HashMap<>();

    public PermutationBuilder(List<T> inputs) {
        this.inputs = inputs;
    }

    public Set<List<T>> get() {
        return this.permutationsFor(this.inputs);
    }

    private Set<List<T>> permutationsFor(List<T> inputs) {
        if (inputs.isEmpty()) {
            return Set.of();
        }

        if (permutationMap.containsKey(inputs)) {
            return permutationMap.get(inputs);
        }

        Set<List<T>> result = new HashSet<>();

        T leftMostElement = inputs.get(0);
        // We need to make sure we're there by ourselves at a bare minimum
        result.add(List.of(leftMostElement));

        List<T> remainderList = inputs.subList(1, inputs.size());
        Set<List<T>> subLists = permutationsFor(remainderList);
        for(var subList : subLists) {
            // Add whatever subList they had made
            result.add(subList);

            // Create a new one including us
            List<T> ourList = new ArrayList<>(List.of(leftMostElement));
            ourList.addAll(subList);
            result.add(ourList);
        }

        permutationMap.put(inputs, result);

        return permutationMap.get(inputs);
    }
}
