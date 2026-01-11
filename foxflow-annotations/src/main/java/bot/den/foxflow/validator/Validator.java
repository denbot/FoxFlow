package bot.den.foxflow.validator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;

import java.util.List;

public interface Validator {
    ClassName originalTypeName();

    ClassName wrappedClassName();

    TypeName pairClassName();

    TypeName timeClassName();

    boolean supportsStateTransition();

    <R> List<R> visitTopLevel(Visitor<R> visitor);

    <R> List<R> visitPermutations(Visitor<R> visitor);

    interface Visitor<T> {
        T acceptUserDataType();
        T acceptFields(RecordValidator validator, List<ClassName> fields);
        T acceptWrapperDataType();
    }
}
