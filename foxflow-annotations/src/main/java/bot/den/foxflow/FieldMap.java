package bot.den.foxflow;

import com.palantir.javapoet.ClassName;

import java.util.List;

public record FieldMap<T>(
        List<Field<T>> fields,
        ClassName dataClass
) {
}
