package bot.den.foxflow.tests;

import bot.den.foxflow.StateMachineAnnotationProcessor;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;

public class CompileFailureTest {
    @Test
    public void transitionToRobotState() {
        var file = JavaFileObjects.forResource("fixtures/ungenerated_code/TransitionToRobotState.java");

        var compilation = Compiler.javac().compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("RobotState")
                .inFile(file)
                .onLineContaining("transitionTo(RobotState");
    }

    @Test
    public void transitionAlwaysWithRobotState() {
        var file = JavaFileObjects.forResource("fixtures/ungenerated_code/TransitionAlwaysWithRobotState.java");

        var compilation = Compiler.javac().compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("method transitionAlways()")
                .inFile(file);
    }

    @Test
    public void cannotHaveTwoEnumElementsMarkedDefaultState() {
        var file = JavaFileObjects.forResource("fixtures/enums/TwoDefaultStateInEnum.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("Cannot put the @DefaultState annotation on more than one element of fixtures.enums.TwoDefaultStateInEnum")
                .inFile(file);
    }

    @Test
    public void cannotHaveEmptyRecord() {
        var file = JavaFileObjects.forResource("fixtures/records/EmptyRecord.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("An empty record isn't supported for building a state machine. Failed to build state machine for fixtures.records.EmptyRecord")
                .inFile(file);
    }

    @Test
    public void recordWithStringField() {
        var file = JavaFileObjects.forResource("fixtures/records/RecordWithStringField.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("Invalid type java.lang.String for field myField in record fixtures.records.RecordWithStringField")
                .inFile(file);
    }

    @Test
    public void recordWithIntField() {
        var file = JavaFileObjects.forResource("fixtures/records/RecordWithIntField.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("Invalid type int for field myField in record fixtures.records.RecordWithIntField")
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
        var file = JavaFileObjects.forResource("fixtures/enums/InvalidLimitsStateTransitions.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("LimitsStateTransitions parameter must be of type fixtures.enums.InvalidLimitsStateTransitions")
                .inFile(file);
    }

    @Test
    public void stringArrayInLimitsSTateTransitions() {
        var file = JavaFileObjects.forResource("fixtures/enums/ArrayLimitsStateTransitions.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("LimitsStateTransitions parameter must be of type fixtures.enums.ArrayLimitsStateTransitions")
                .inFile(file);
    }

    @Test
    public void nonGenericLimitsStateTransitions() {
        var file = JavaFileObjects.forResource("fixtures/enums/NonGenericLimitsStateTransitions.java");

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
    public void stateMachineNameCollision() {
        var myEnum = JavaFileObjects.forResource("fixtures/enums/Collision.java");
        var stateMachine = JavaFileObjects.forResource("fixtures/enums/CollisionStateMachine.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(myEnum, stateMachine);

        assertThat(compilation)
                .hadErrorContaining("Failed to write class fixtures.enums.CollisionStateMachine")
                .inFile(myEnum);
    }

    @Test
    public void twoFieldsWithSameTypeIsInvalid() {
        var file = JavaFileObjects.forResource("fixtures/records/TwoFieldsSameType.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(file);

        assertThat(compilation)
                .hadErrorContaining("Cannot have more than one field of type bot.den.foxflow.tests.BasicEnum in record fixtures.records.TwoFieldsSameType")
                .inFile(file);
    }

    @Test
    public void emptyEnum() {
        var file = JavaFileObjects.forResource("fixtures/enums/EmptyEnum.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("fixtures.enums.EmptyEnum must have at least one enum constant")
                .inFile(file);
    }

    @Test
    public void defaultTestOnNonEnumConstant() {
        var file = JavaFileObjects.forResource("fixtures/enums/DefaultStateOnNonEnumConstant.java");

        var compilation = Compiler.javac()
                .withProcessors(new StateMachineAnnotationProcessor())
                .compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("@DefaultState must only be used on enum constants")
                .inFile(file);
    }
}
