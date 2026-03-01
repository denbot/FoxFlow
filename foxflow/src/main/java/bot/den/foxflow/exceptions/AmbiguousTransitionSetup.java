package bot.den.foxflow.exceptions;

/**
 * Thrown during state machine setup when FoxFlow detects that a transition would be ambiguous at
 * runtime. For example, if the same condition is used to transition from state A to both B and C,
 * FoxFlow cannot determine which target state to use.
 *
 * <p>This exception is designed to surface invalid configurations early, during setup, rather than
 * failing unpredictably during a state transition. Note that not all ambiguous setups are
 * guaranteed to be detected.
 */
public class AmbiguousTransitionSetup extends RuntimeException {
    /**
     * @param fromState the state from which the ambiguous transition originates
     * @param newState the state that was being set up to transition to
     * @param conflictingState the state that was already configured to transition to under the same conditions
     */
    public AmbiguousTransitionSetup(Object fromState, Object newState, Object conflictingState) {
        super("Cannot setup transition from " + fromState + " to " + newState + " because " + conflictingState + " was already setup to transition under the same conditions");
    }
}
