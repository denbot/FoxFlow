package bot.den.foxflow.validator;

import bot.den.foxflow.builders.FieldHelper;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;

public interface Validator {
    ClassName originalTypeName();

    ClassName wrappedClassName();

    TypeName pairClassName();

    TypeName timeClassName();

    boolean supportsStateTransition();

    <R> FieldHelper<R> newFieldHelper();
}
