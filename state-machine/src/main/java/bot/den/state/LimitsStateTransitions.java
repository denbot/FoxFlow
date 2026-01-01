package bot.den.state;

import bot.den.state.exceptions.InvalidStateTransition;

public interface LimitsStateTransitions<T> {
    boolean canTransitionTo(T newState);

    default void attemptTransitionTo(T newState) throws InvalidStateTransition {
        if(!this.canTransitionTo(newState)) {
            throw new InvalidStateTransition(this, newState);
        }
    }
}
