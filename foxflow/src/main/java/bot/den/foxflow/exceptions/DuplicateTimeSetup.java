package bot.den.foxflow.exceptions;

import org.wpilib.units.measure.Time;

/**
 * Thrown during state machine setup when FoxFlow detects that a transition after a certain amount of time has already
 * been set up with the same specified fromState. This does not depend on the state you're transitioning to at all.
 */
public class DuplicateTimeSetup extends RuntimeException {
    public DuplicateTimeSetup(Object fromState, Time originalTime, Time newTime) {
        super("Cannot setup second transition from " + fromState + " with time " + newTime + "as a transition for " + originalTime + " was already set up for it");
    }
}
