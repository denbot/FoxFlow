package bot.den.state.exceptions;

public class InvalidStateTransition extends RuntimeException {
    public InvalidStateTransition(Object fromState, Object toState) {
        super("Cannot transition from " + fromState + " to " + toState);
    }
}
