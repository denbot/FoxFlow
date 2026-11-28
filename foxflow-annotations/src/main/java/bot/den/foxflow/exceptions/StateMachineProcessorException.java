package bot.den.foxflow.exceptions;

public abstract class StateMachineProcessorException extends RuntimeException {
    public StateMachineProcessorException(String message) {
        super(message);
    }
}
