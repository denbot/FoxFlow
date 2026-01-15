package bot.den.foxflow.validator;

import bot.den.foxflow.builders.Builder;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;

import java.util.List;

public interface Validator {
    ClassName originalTypeName();

    ClassName wrappedClassName();

    TypeName pairClassName();

    TypeName timeClassName();

    boolean supportsStateTransition();

    <R> Builder<R> newBuilder();
}
