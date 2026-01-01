package bot.den.state.tests.implement;

import bot.den.state.exceptions.InvalidStateTransition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MyRecordTest {
    @Test
    void canTransitionTypeIsCalled() {
        var machine = new MyRecordStateMachine(Shapes.SQUARE);

        // This is disallowed in our canTransitionType method on the Shapes class, so we don't even have to wait for the
        // state to transition. Merely trying to set up that transition will fail.
        assertThrows(InvalidStateTransition.class, () -> machine.state(Shapes.SQUARE).to(Flavors.Strawberry));
    }
}
