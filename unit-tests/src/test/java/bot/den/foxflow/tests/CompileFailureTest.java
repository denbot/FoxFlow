package bot.den.foxflow.tests;

import bot.den.foxflow.StateMachineAnnotationProcessor;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;

public class CompileFailureTest {
    @Test
    public void transitionToRobotState() {
        var file = JavaFileObjects.forResource("fixtures/TransitionToRobotState.java");

        var compilation = Compiler.javac().compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("RobotState")
                .inFile(file)
                .onLineContaining("transitionTo(RobotState");
    }

    @Test
    public void transitionAlwaysWithRobotState() {
        var file = JavaFileObjects.forResource("fixtures/TransitionAlwaysWithRobotState.java");

        var compilation = Compiler.javac().compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("method transitionAlways()")
                .inFile(file);
    }

    @Test
    public void cannotHaveTwoEnumElementsMarkedDefaultState() {
        var file = JavaFileObjects.forResource("fixtures/TwoDefaultStateInEnum.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("Cannot put the @DefaultState annotation on more than one element of the same enum")
                .inFile(file);
    }

    @Test
    public void cannotHaveEmptyRecord() {
        var file = JavaFileObjects.forResource("fixtures/EmptyRecord.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("An empty record isn't supported for building a state machine. Failed to build state machine for fixtures.EmptyRecord")
                .inFile(file);
    }

    @Test
    public void recordWithStringField() {
        var file = JavaFileObjects.forResource("fixtures/RecordWithStringField.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("Invalid type java.lang.String for field myField in record fixtures.RecordWithStringField")
                .inFile(file);
    }

    @Test
    public void recordWithIntField() {
        var file = JavaFileObjects.forResource("fixtures/RecordWithIntField.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("Invalid type int for field myField in record fixtures.RecordWithIntField")
                .inFile(file);
    }

    @Test
    public void stateMachineAnnotationOnClass() {
        var file = JavaFileObjects.forResource("fixtures/StateMachineOnClass.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("The StateMachine annotation is only valid on enums and records")
                .inFile(file);
    }

    @Test
    public void stateMachineAnnotationOnInterface() {
        var file = JavaFileObjects.forResource("fixtures/StateMachineOnInterface.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("The StateMachine annotation is only valid on enums and records")
                .inFile(file);
    }

    @Test
    public void invalidLimitsStateTransitions() {
        var file = JavaFileObjects.forResource("fixtures/InvalidLimitsStateTransitions.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("LimitsStateTransitions parameter must be of type fixtures.InvalidLimitsStateTransitions")
                .inFile(file);
    }

    @Test
    public void stringArrayInLimitsSTateTransitions() {
        var file = JavaFileObjects.forResource("fixtures/ArrayLimitsStateTransitions.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("LimitsStateTransitions parameter must be of type fixtures.ArrayLimitsStateTransitions")
                .inFile(file);
    }

    @Test
    public void nonGenericLimitsStateTransitions() {
        var file = JavaFileObjects.forResource("fixtures/NonGenericLimitsStateTransitions.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("The LimitsStateTransitions interface should have one type argument")
                .inFile(file);
    }

    @Test
    public void cannotPutRecordAsRecordField() {
        var outer = JavaFileObjects.forResource("fixtures/child_records/OuterRecord.java");
        var inner = JavaFileObjects.forResource("fixtures/child_records/InnerRecord.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(outer, inner);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("Invalid type fixtures.child_records.InnerRecord for field myInnerRecord in record fixtures.child_records.OuterRecord")
                .inFile(outer);
    }

    @Test
    public void collision() {
        var myEnum = JavaFileObjects.forResource("fixtures/Collision.java");
        var stateMachine = JavaFileObjects.forResource("fixtures/CollisionStateMachine.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(myEnum, stateMachine);

        assertThat(compilation)
                .hadErrorContaining("Failed to write class fixtures.CollisionStateMachine")
                .inFile(myEnum);
    }
}
