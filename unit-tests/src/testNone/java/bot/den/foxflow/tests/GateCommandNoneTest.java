package bot.den.foxflow.tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wpilib.hardware.hal.HAL;
import org.wpilib.simulation.SimHooks;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class GateCommandNoneTest {
    private BasicRecordStateMachine machine;
    private AtomicInteger runnableRan;

    // Our command that will be gated
    private Runnable command;

    @BeforeEach
    public void setup() {
        assertTrue(HAL.initialize());

        // Just for our timing tests, but it's a good thing to verify
        assertFalse(SimHooks.isTimingPaused());

        machine = new BasicRecordStateMachine(MultiStateEnum.A, BasicEnum.START, BasicRecord.InnerEnum.CIRCLE);

        runnableRan = new AtomicInteger();

        command = () -> runnableRan.incrementAndGet();
    }

    @AfterEach
    public void cleanup() {
        Actions.reset();
    }

    @Test
    public void runnableRunsIfInCorrectState() {
        var gatingCommand = machine.state(MultiStateEnum.A).gatesCommand(command);

        gatingCommand.run();

        assertEquals(1, runnableRan.get());
    }

    @Test
    public void runnableDoesNotRunIfInIncorrectState() {
        var gatingCommand = machine.state(MultiStateEnum.B).gatesCommand(command);

        gatingCommand.run();

        assertEquals(0, runnableRan.get());
    }
}
