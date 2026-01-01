# Denbot State Machine

A type-safe state machine library for FRC robots that generates code at compile time using Java annotations.

# Installation

*To be filled in*

# Usage

## Enum Based States

### Defining a state enum

If you want to track state for a single subsystem, you can use an enum. The `@StateMachine` annotation tells the library to generate a state machine class for you at compile time.

Here's an intake subsystem with four states:

```java
import bot.den.state.StateMachine;

@StateMachine
public enum IntakeState {
    IDLE,      // Intake is stopped
    INTAKING,  // Actively pulling in game piece
    INTAKEN,   // Game piece is secured
    HANDOFF    // Transferring to another mechanism
}
```

### Creating the state machine

```java
IntakeStateStateMachine stateMachine = new IntakeStateStateMachine(IntakeState.IDLE);
```

### Executing transitions

**You must use one of these options or no state transitions will occur:**

**Option 1: Manual polling (recommended for precise timing)**
```java
@Override
public void robotPeriodic() {
    stateMachine.poll();  // Check and execute transitions first
    CommandScheduler.getInstance().run();  // Then run scheduled commands
}
```

This ensures transitions happen immediately. Commands scheduled by transitions will run on the next scheduler cycle.

**Option 2: Using runPollCommand**
```java
@Override
public void robotInit() {
    CommandScheduler.getInstance().schedule(stateMachine.runPollCommand());
}
```

This is simpler, but commands scheduled during a transition won't execute until the next loop cycle because the CommandScheduler doesn't run commands that are scheduled while it's already running commands.

### Unconditional transitions

Set up a transition that always happens when the condition is checked:

```java
stateMachine
    .state(IntakeState.IDLE)
    .to(IntakeState.INTAKING)
    .transitionAlways();
```

### Conditional transitions

Transitions can depend on sensors or button presses:

**Sensor-based transitions:**
```java
stateMachine
    .state(IntakeState.INTAKING)
    .to(IntakeState.INTAKEN)
    .transitionWhen(() -> sensor.hasGamePiece());
```

**Button-based transitions:**
```java
stateMachine
    .state(IntakeState.IDLE)
    .to(IntakeState.INTAKING)
    .transitionWhen(controller.a());
```

The second example uses a Trigger from `CommandXboxController`. Any `BooleanSupplier` works here.

### Manual state transitions as Commands

You can manually force a state change using a Command:

```java
Command intakeCommand = stateMachine.transitionTo(IntakeState.INTAKING);
CommandScheduler.getInstance().schedule(intakeCommand);
```

Use this when you want to control the transition directly from a command rather than using automatic transitions.

### Running commands on transitions

You can schedule commands when a state transition occurs:

```java
stateMachine
    .state(IntakeState.IDLE)
    .to(IntakeState.INTAKING)
    .transitionAlways()
    .run(Commands.runOnce(() -> intake.setSpeed(0.8)));
```

You can also chain state transitions with other commands:

```java
Command sequence = moveIntakeDown()
    .andThen(stateMachine.transitionTo(IntakeState.INTAKING));

sequence.schedule();
```

This is useful when you need a mechanical movement to complete before changing state.

## Record Based States

### Defining a composite state

Records let you combine multiple state machines into one coordinated state:

```java
import bot.den.state.RobotState;
import bot.den.state.StateMachine;

@StateMachine
public record GameState(
    RobotState robotState,
    IntakeState intakeState
) {}
```

This creates a state machine that tracks both the robot's mode (auto/teleop/disabled) and the intake state.

### Partial state transitions

You can transition just one field while leaving the others unchanged:

```java
GameStateStateMachine stateMachine = new GameStateStateMachine(IntakeState.IDLE);

// Only change the intake state
stateMachine
    .state(IntakeState.IDLE)
    .to(IntakeState.INTAKING)
    .transitionAlways();
```

The `robotState` field remains unchanged when this transition runs.

### RobotState behavior (important!)

`RobotState` is special - it's controlled by the driver station, not your code:

- **Cannot** use `RobotState` in the constructor
- **Cannot** use `RobotState` with `.transitionTo()`
- **Can** use `RobotState` with `.state()` and `.to()` to limit when transitions happen

```java
// This works - limits when the transition can run
stateMachine
    .state(RobotState.AUTO)
    .to(IntakeState.INTAKING)
    .transitionAlways();

// This throws an error - you can't manually control RobotState
Command invalid = stateMachine.transitionTo(RobotState.AUTO);  // ❌ Won't compile
```

You can also use `RobotState` in both `.state()` and `.to()`:

```java
// This is valid - run a command when transitioning from DISABLED to AUTO
stateMachine
    .state(RobotState.DISABLED)
    .to(RobotState.AUTO)
    .run(startAutoCommand);
```

However, when using `RobotState` in the `.to()` clause, you won't have access to `.transitionWhen()` or `.transitionAlways()` since the robot state transition is controlled by the driver station.

### Conditional execution based on robot mode

You can use `RobotState` to make transitions only happen in specific modes:

```java
// Only run the auto intake sequence in autonomous
stateMachine
    .state(RobotState.AUTO, IntakeState.IDLE)
    .to(IntakeState.INTAKING)
    .transitionWhen(() -> autoTimer.hasElapsed(2.0));

// Only allow manual intaking in teleop
stateMachine
    .state(RobotState.TELEOP, IntakeState.IDLE)
    .to(IntakeState.INTAKING)
    .transitionWhen(controller.a());
```

This prevents teleop commands from interfering with autonomous routines.

## Limiting State Transitions

### Implementing LimitsStateTransitions on enums

By default, any state can transition to any other state. You can restrict this by implementing `LimitsStateTransitions`:

```java
import bot.den.state.LimitsStateTransitions;
import bot.den.state.StateMachine;

@StateMachine
public enum IntakeState implements LimitsStateTransitions<IntakeState> {
    IDLE,
    INTAKING,
    INTAKEN,
    HANDOFF;

    @Override
    public boolean canTransitionState(IntakeState newState) {
        return switch (this) {
            case IDLE -> newState == INTAKING;
            case INTAKING -> newState == INTAKEN;
            case INTAKEN -> newState == HANDOFF;
            case HANDOFF -> newState == IDLE;
        };
    }
}
```

This creates a specific flow: IDLE → INTAKING → INTAKEN → HANDOFF → IDLE.

**Important:** Implementing `LimitsStateTransitions` only restricts which transitions are *allowed* - it doesn't make transitions happen automatically. You still need to set up when transitions occur using `.transitionWhen()`, `.transitionAlways()`, or `.transitionTo()`.

### When InvalidStateTransition is thrown

The exception is thrown **when you set up the transition**, not when it runs:

```java
// This throws InvalidStateTransition immediately
stateMachine
    .state(IntakeState.IDLE)
    .to(IntakeState.HANDOFF);  // ❌ Not allowed by canTransitionState
```

This is helpful because your code will fail at startup if you have invalid transitions, rather than failing mysteriously during a match.

## Failing Loudly for Safety

Sometimes you want to completely crash the robot code if a dangerous transition is attempted, even if the transition would normally be valid:

```java
stateMachine
    .state(IntakeState.IDLE)
    .to(IntakeState.INTAKING)
    .transitionAlways()
    .failLoudly();
```

If this transition is ever triggered, it will throw an exception and stop your code. Use this when continuing after a bad transition could damage your robot or be unsafe. For example, you might fail loudly if the intake tries to run while the shooter is firing.

## Using Interfaces in Records

### Defining an interface field

Interfaces let you have different types of data in the same field:

```java
public interface GamePieceTarget {
}
```

```java
import bot.den.state.StateMachine;

@StateMachine
public record GridPosition(int row, int column) implements GamePieceTarget {
}
```

```java
import bot.den.state.StateMachine;

@StateMachine
public enum GroundIntakeZone implements GamePieceTarget {
    LEFT,
    CENTER,
    RIGHT
}
```

```java
import bot.den.state.StateMachine;

@StateMachine
public record ScoringState(GamePieceTarget target) {
}
```

Now `target` can be either a `GridPosition` or a `GroundIntakeZone`.

### Transitions between implementations

You can transition between different types through the interface:

```java
ScoringStateStateMachine stateMachine =
    new ScoringStateStateMachine(GroundIntakeZone.CENTER);

// Transition from enum to record
stateMachine
    .state(GroundIntakeZone.CENTER)
    .to(new GridPosition(2, 4))
    .transitionWhen(() -> hasGamePiece());

// Transition within the same type
stateMachine
    .state(GroundIntakeZone.LEFT)
    .to(GroundIntakeZone.RIGHT)
    .transitionAlways();
```

### Limiting State Transitions on records

Records can also implement `LimitsStateTransitions` to control transitions:

```java
import bot.den.state.LimitsStateTransitions;
import bot.den.state.StateMachine;

@StateMachine
public record GridPosition(int row, int column)
    implements GamePieceTarget, LimitsStateTransitions<GridPosition> {

    @Override
    public boolean canTransitionState(GridPosition newState) {
        // Only allow moving to adjacent grid positions
        int rowDiff = Math.abs(this.row - newState.row);
        int colDiff = Math.abs(this.column - newState.column);
        return (rowDiff <= 1 && colDiff == 0) || (rowDiff == 0 && colDiff <= 1);
    }
}
```

This prevents jumping across the grid in a single transition. Remember, this only restricts which transitions are allowed - you still need to set up when transitions occur.

### Limiting Type Transitions

You can also control which types can transition to which other types:

```java
import bot.den.state.LimitsStateTransitions;
import bot.den.state.LimitsTypeTransitions;
import bot.den.state.StateMachine;

@StateMachine
public record GridPosition(int row, int column)
    implements GamePieceTarget,
               LimitsStateTransitions<GridPosition>,
               LimitsTypeTransitions<GridPosition> {

    @Override
    public boolean canTransitionState(GridPosition newState) {
        int rowDiff = Math.abs(this.row - newState.row);
        int colDiff = Math.abs(this.column - newState.column);
        return (rowDiff <= 1 && colDiff == 0) || (rowDiff == 0 && colDiff <= 1);
    }

    @Override
    public boolean canTransitionType(Object other) {
        // Can transition to GroundIntakeZone
        if (other instanceof GroundIntakeZone) {
            return true;
        }
        // Cannot transition to other types
        return false;
    }
}
```

Now you can go from `GridPosition` to `GroundIntakeZone`, but if you tried to make a third type that implements `GamePieceTarget`, `GridPosition` wouldn't be able to transition to it.

Like with `LimitsStateTransitions`, this only controls which transitions are allowed, not when they happen.

## Advanced Features

### Triggers for button binding

You can create triggers that activate when the state machine enters a specific state:

```java
Trigger inIntakingState = stateMachine.state(IntakeState.INTAKING).trigger();

// Light up LEDs when intaking
inIntakingState.onTrue(Commands.runOnce(() -> leds.setColor(Color.kGreen)));

// Turn off LEDs when leaving the state
inIntakingState.onFalse(Commands.runOnce(() -> leds.setColor(Color.kBlack)));
```

This is useful for driver feedback, LED indicators, or automatically scheduling commands when entering specific states.
