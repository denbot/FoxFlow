package bot.den.foxflow.tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.wpilib.command2.Command;
import org.wpilib.hardware.hal.HAL;
import org.wpilib.simulation.SimHooks;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class GateCommandV2Test {
    private BasicRecordStateMachine machine;
    private AtomicInteger initializerRan;
    private AtomicInteger executeRan;
    private AtomicInteger endRan;
    private AtomicBoolean lastEndInterrupted;
    private AtomicBoolean isFinished;

    // Our command that will be gated
    private Command command;

    @BeforeEach
    public void setup() {
        assertTrue(HAL.initialize());

        // Just for our timing tests, but it's a good thing to verify
        assertFalse(SimHooks.isTimingPaused());

        machine = new BasicRecordStateMachine(MultiStateEnum.A, BasicEnum.START, BasicRecord.InnerEnum.CIRCLE);

        initializerRan = new AtomicInteger();
        executeRan = new AtomicInteger();
        endRan = new AtomicInteger();
        lastEndInterrupted = new AtomicBoolean();
        isFinished = new AtomicBoolean();

        command = new Command() {
            @Override
            public void initialize() {
                initializerRan.incrementAndGet();
            }

            @Override
            public void execute() {
                executeRan.incrementAndGet();
            }

            @Override
            public void end(boolean interrupted) {
                endRan.incrementAndGet();
                lastEndInterrupted.set(interrupted);
            }

            @Override
            public boolean isFinished() {
                return isFinished.get();
            }
        };
    }

    @AfterEach
    public void cleanup() {
        Actions.reset();
    }

    @Test
    public void commandWillInitializeIfWeAreInTheCorrectState() {
        var gatingCommand = machine.state(MultiStateEnum.A).gatesCommand(command);

        gatingCommand.initialize();

        assertEquals(1, initializerRan.get());
        assertEquals(0, executeRan.get());
        assertEquals(0, endRan.get());
        assertFalse(lastEndInterrupted.get());
        assertFalse(gatingCommand.isFinished());
    }

    @Test
    public void commandWillNotInitializeIfWeAreInTheIncorrectState() {
        var gatingCommand = machine.state(MultiStateEnum.B).gatesCommand(command);

        gatingCommand.initialize();

        assertEquals(0, initializerRan.get());
        assertEquals(0, executeRan.get());
        // The scheduler calls end, so this is still 0 / false
        assertEquals(0, endRan.get());
        assertFalse(lastEndInterrupted.get());
        // However isFinished checks the state directly
        assertTrue(gatingCommand.isFinished());
    }

    @Test
    public void gatingCommandIsFinishedIfGatedCommandIsFinished() {
        var gatingCommand = machine.state(MultiStateEnum.A).gatesCommand(command);

        assertEquals(0, initializerRan.get());
        assertEquals(0, executeRan.get());
        assertEquals(0, endRan.get());
        assertFalse(lastEndInterrupted.get());

        // isFinished is false because we're in the correct state
        assertFalse(gatingCommand.isFinished());

        // Next set the gated command to be finished and check again
        isFinished.set(true);
        assertTrue(gatingCommand.isFinished());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void gatingCommandEndCallsGatedCommandsEndIfInTheCorrectState(boolean interrupted) {
        var gatingCommand = machine.state(MultiStateEnum.A).gatesCommand(command);

        assertEquals(0, initializerRan.get());
        assertEquals(0, executeRan.get());

        gatingCommand.end(interrupted);

        assertEquals(1, endRan.get());
        assertEquals(interrupted, lastEndInterrupted.get());
        // We don't check isFinished because we don't actually care in this case.
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void gatingCommandEndCallsGatedCommandsEndIfInTheIncorrectState(boolean interrupted) {
        var gatingCommand = machine.state(MultiStateEnum.B).gatesCommand(command);

        assertEquals(0, initializerRan.get());
        assertEquals(0, executeRan.get());

        gatingCommand.end(interrupted);

        assertEquals(1, endRan.get());
        assertEquals(interrupted, lastEndInterrupted.get());
        // We don't check isFinished because we don't actually care in this case.
    }

    @Test
    public void executeRunsIfInTheCorrectState() {
        var gatingCommand = machine.state(MultiStateEnum.A).gatesCommand(command);

        gatingCommand.execute();

        assertEquals(0, initializerRan.get());
        assertEquals(1, executeRan.get());
        assertEquals(0, endRan.get());
        assertFalse(lastEndInterrupted.get());
        assertFalse(gatingCommand.isFinished());
    }

    @Test
    public void executeDoesNotRunIfInTheIncorrectState() {
        var gatingCommand = machine.state(MultiStateEnum.B).gatesCommand(command);

        gatingCommand.execute();

        assertEquals(0, initializerRan.get());
        assertEquals(0, executeRan.get());
        assertEquals(0, endRan.get());
        assertFalse(lastEndInterrupted.get());
        assertTrue(gatingCommand.isFinished());
    }
}
