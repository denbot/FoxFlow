package bot.den.foxflow.tests;

import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;

public class CompileFailureNoneTest {
    @Test
    public void noneVersionHasNoTrigger() {
        var file = JavaFileObjects.forResource("fixtures/Trigger.java");

        var compilation = Compiler.javac().compile(file);

        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("method trigger()")
                .inFile(file);
    }
}
