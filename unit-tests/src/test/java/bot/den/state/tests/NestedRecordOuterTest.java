package bot.den.state.tests;

import edu.wpi.first.hal.HAL;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import edu.wpi.first.wpilibj2.command.Commands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class NestedRecordOuterTest {
    private NestedRecordOuterStateMachine machine;

    @BeforeEach
    public void setup() {
        assertTrue(HAL.initialize(500, 0));

        this.machine = new NestedRecordOuterStateMachine(
                new NestedRecordOuter.NestedRecord(MultiStateEnum.A)
        );
    }

    @AfterEach
    public void cleanup() {
        CommandScheduler.getInstance().cancelAll();
        CommandScheduler.getInstance().run();
    }

    @Test
    void canCreateMachineWithNestedRecord() {
        // Verify initial state
        var state = this.machine.currentState();
        assertEquals(MultiStateEnum.A, state.nested().multiStateEnum());
    }

    @Test
    void canTransitionNestedRecordField() {
        // Force a transition on the nested record field
        var command = machine.transitionTo(new NestedRecordOuter.NestedRecord(MultiStateEnum.B));
        CommandScheduler.getInstance().schedule(command);

        // Verify the nested field changed
        assertEquals(MultiStateEnum.B, this.machine.currentState().nested().multiStateEnum());
    }

    @Test
    void transitionWhenWorksOnNestedRecord() {
        final AtomicBoolean test = new AtomicBoolean(false);

        // Set up conditional transition on nested record
        this.machine
                .state(new NestedRecordOuter.NestedRecord(MultiStateEnum.A))
                .to(new NestedRecordOuter.NestedRecord(MultiStateEnum.B))
                .transitionWhen(test::get);

        // Verify no transition yet
        machine.poll();
        assertEquals(MultiStateEnum.A, this.machine.currentState().nested().multiStateEnum());

        // Enable the transition
        test.set(true);
        machine.poll();

        // Verify transition occurred
        assertEquals(MultiStateEnum.B, this.machine.currentState().nested().multiStateEnum());
    }

    @Test
    void runCommandOnNestedRecordTransition() {
        final AtomicBoolean test = new AtomicBoolean(false);

        // Set up transition with command
        this.machine
                .state(new NestedRecordOuter.NestedRecord(MultiStateEnum.A))
                .to(new NestedRecordOuter.NestedRecord(MultiStateEnum.B))
                .transitionAlways()
                .run(Commands.runOnce(() -> test.set(true)).ignoringDisable(true));

        machine.poll();

        // Command should have run
        assertTrue(test.get());
        assertEquals(MultiStateEnum.B, this.machine.currentState().nested().multiStateEnum());
    }

    @Test
    void triggerOnNestedRecordState() {
        // Create trigger for nested record state
        var trigger = this.machine
                .state(new NestedRecordOuter.NestedRecord(MultiStateEnum.B))
                .trigger();

        assertFalse(trigger.getAsBoolean());

        // Transition to the target state
        var command = this.machine.transitionTo(new NestedRecordOuter.NestedRecord(MultiStateEnum.B));
        CommandScheduler.getInstance().schedule(command);

        assertTrue(trigger.getAsBoolean());
    }
}
