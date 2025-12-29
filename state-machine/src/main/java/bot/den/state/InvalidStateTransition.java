package bot.den.state;

public class InvalidStateTransition extends RuntimeException {
    public InvalidStateTransition(Object fromState, Object toState) {
        super("Cannot transition from " + fromState + " to " + toState);
    }
}
