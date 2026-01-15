package bot.den.foxflow.tests.defaults;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RecordWithDefaultFieldsStateMachineTest {
    @Test
    void constructorAllDefaulted() {
        var machine = new RecordWithDefaultFieldsStateMachine();

        assertEquals(DefaultLetter.A, machine.currentState().letter());
        assertEquals(DefaultNumber.Two, machine.currentState().number());
        assertEquals(DefaultLatin.Gamma, machine.currentState().latin());
    }

    @Test
    void testConstructorLetterOnly() {
        var machine = new RecordWithDefaultFieldsStateMachine(DefaultLetter.B);

        assertEquals(DefaultLetter.B, machine.currentState().letter());
        assertEquals(DefaultNumber.Two, machine.currentState().number());
        assertEquals(DefaultLatin.Gamma, machine.currentState().latin());
    }

    @Test
    void testConstructorNumberOnly() {
        var machine = new RecordWithDefaultFieldsStateMachine(DefaultNumber.One);

        assertEquals(DefaultLetter.A, machine.currentState().letter());
        assertEquals(DefaultNumber.One, machine.currentState().number());
        assertEquals(DefaultLatin.Gamma, machine.currentState().latin());
    }

    @Test
    void testConstructorLatinOnly() {
        var machine = new RecordWithDefaultFieldsStateMachine(DefaultLatin.Beta);

        assertEquals(DefaultLetter.A, machine.currentState().letter());
        assertEquals(DefaultNumber.Two, machine.currentState().number());
        assertEquals(DefaultLatin.Beta, machine.currentState().latin());
    }
    @Test
    void testConstructorLetterAndNumberOnly() {
        var machine = new RecordWithDefaultFieldsStateMachine(DefaultLetter.B, DefaultNumber.Three);

        assertEquals(DefaultLetter.B, machine.currentState().letter());
        assertEquals(DefaultNumber.Three, machine.currentState().number());
        assertEquals(DefaultLatin.Gamma, machine.currentState().latin());
    }

    @Test
    void testConstructorLetterAndLatinOnly() {
        var machine = new RecordWithDefaultFieldsStateMachine(DefaultNumber.One, DefaultLatin.Alpha);

        assertEquals(DefaultLetter.A, machine.currentState().letter());
        assertEquals(DefaultNumber.One, machine.currentState().number());
        assertEquals(DefaultLatin.Alpha, machine.currentState().latin());
    }

    @Test
    void testConstructorNumberAndLatinOnly() {
        var machine = new RecordWithDefaultFieldsStateMachine(DefaultNumber.One, DefaultLatin.Beta);

        assertEquals(DefaultLetter.A, machine.currentState().letter());
        assertEquals(DefaultNumber.One, machine.currentState().number());
        assertEquals(DefaultLatin.Beta, machine.currentState().latin());
    }

    @Test
    void constructorNoneDefaulted() {
        var machine = new RecordWithDefaultFieldsStateMachine(DefaultLetter.C, DefaultNumber.One, DefaultLatin.Beta);

        assertEquals(DefaultLetter.C, machine.currentState().letter());
        assertEquals(DefaultNumber.One, machine.currentState().number());
        assertEquals(DefaultLatin.Beta, machine.currentState().latin());
    }
}
