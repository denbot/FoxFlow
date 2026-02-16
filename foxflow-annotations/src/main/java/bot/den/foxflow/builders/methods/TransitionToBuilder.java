package bot.den.foxflow.builders.methods;

import bot.den.foxflow.Field;
import bot.den.foxflow.builders.FieldHelper;
import bot.den.foxflow.builders.Names;
import bot.den.foxflow.builders.TypedBuilder;
import bot.den.foxflow.validator.EnumValidator;
import bot.den.foxflow.validator.RecordValidator;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;

public class TransitionToBuilder implements TypedBuilder<List<MethodSpec>> {
    private final FieldHelper<MethodSpec> transitionToMethods;
    private final TransitionToCode transitionToCode;
    private final Names names;

    public TransitionToBuilder(
            Names names,
            TransitionToCode transitionToCode
    ) {
        this.names = names;
        transitionToMethods = names.validator().newFieldHelper();
        this.transitionToCode = transitionToCode;

        addUserDataType();
        addInternalDataCode();
        addFieldPermutations();
    }

    private void addUserDataType() {
        transitionToMethods.userDataType(() -> {
            if (names.validator() instanceof EnumValidator) {
                return MethodSpec
                        .methodBuilder("transitionTo")
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(names.validator().originalTypeName(), "state")
                        .returns(transitionToCode.returnType())
                        .addCode(transitionToCode.enumCode())
                        .build();
            } else if (names.validator() instanceof RecordValidator) {
                /*
                We don't make a transitionTo method for a record as the record could contain the RobotState and the
                user should not be able to force that transition. We could theoretically check if the record had a
                RobotState as one of its components, but then the method might "disappear" from the user's
                perspective. We could ignore the robot state when updating our internal state, but that might be
                confusing for the user who either expected that transition to hold or didn't know what value to put
                for Robot State.
                 */
                return null;
            } else {
                throw new RuntimeException("Unknown validator type");
            }
        });
    }

    private void addInternalDataCode() {
        CodeBlock internalDataCode = transitionToCode.internalData();
        if (internalDataCode.isEmpty()) {
            return;
        }

        transitionToMethods.wrappedType(() -> MethodSpec
                .methodBuilder("transitionTo")
                .addModifiers(Modifier.PRIVATE)
                .addParameter(names.validator().wrappedClassName(), "state")
                .returns(transitionToCode.returnType())
                .addCode(transitionToCode.internalData())
                .build());
    }

    private void addFieldPermutations() {
        transitionToMethods.permuteFields(FieldHelper.optional)
                .fields((fields, className) -> {
                    if (!(names.validator() instanceof RecordValidator rv)) {
                        throw new UnsupportedOperationException("This method should not have been called with a non-record validator");
                    }

                    // Can't transition to an empty list
                    if (className == null) {
                        return null;
                    }

                    boolean hasRobotStateField = fields.stream().anyMatch(f -> f.value().equals(names.robotStateName()));
                    if (hasRobotStateField) {
                        // We don't want to allow the user to ever transition to a specific RobotState
                        return null;
                    }

                    MethodSpec.Builder methodBuilder = MethodSpec
                            .methodBuilder("transitionTo")
                            .addModifiers(Modifier.PUBLIC)
                            .returns(transitionToCode.returnType());

                    for (var field : fields) {
                        methodBuilder.addParameter(field.value(), field.name());
                    }

                    return methodBuilder
                            .addCode(transitionToCode.fields(rv, fields))
                            .build();
                });

    }

    @Override
    public List<MethodSpec> build() {
        List<MethodSpec> result = new ArrayList<>();
        transitionToMethods.iterator().forEachRemaining(result::add);
        return result;
    }

    public interface TransitionToCode {
        CodeBlock enumCode();

        CodeBlock internalData();

        CodeBlock fields(RecordValidator recordValidator, List<Field<ClassName>> fields);

        TypeName returnType();
    }
}
