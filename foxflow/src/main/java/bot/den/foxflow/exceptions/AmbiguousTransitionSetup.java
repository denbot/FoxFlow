package bot.den.foxflow.exceptions;

public class AmbiguousTransitionSetup extends RuntimeException {
    public AmbiguousTransitionSetup(Object fromState, Object newState, Object conflictingState) {
        super("Cannot setup transition from " + fromState + " to " + newState + " because " + conflictingState + " was already setup to transition under the same conditions");
    }
}
