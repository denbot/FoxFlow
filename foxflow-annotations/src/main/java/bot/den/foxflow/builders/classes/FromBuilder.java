package bot.den.foxflow.builders.classes;

import bot.den.foxflow.builders.FieldHelper;
import bot.den.foxflow.builders.Names;
import bot.den.foxflow.builders.TypedBuilder;
import bot.den.foxflow.validator.RecordValidator;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import edu.wpi.first.wpilibj.event.EventLoop;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import edu.wpi.first.wpilibj2.command.button.Trigger;

import javax.lang.model.element.Modifier;

public class FromBuilder implements TypedBuilder<TypeSpec> {
    private final TypeSpec.Builder builder;
    private final Names names;

    public FromBuilder(
            Names names
    ) {
        this.names = names;
        builder = TypeSpec.classBuilder(names.fromClassName())
                .addModifiers(Modifier.PUBLIC);

        addFields();
        addConstructor();
        addToMethods();
        addTriggerMethods();
    }

    private void addFields() {
        builder.addField(FieldSpec
                .builder(names.managerClassName(), "manager")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build()
        );

        builder.addField(FieldSpec
                .builder(names.dataTypeName(), "targetState")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build()
        );
    }

    private void addConstructor() {
        builder.addMethod(MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(names.managerClassName(), "manager")
                .addParameter(names.dataTypeName(), "state")
                .addStatement("this.targetState = state")
                .addStatement("this.manager = manager")
                .build()
        );
    }

    private void addToMethods() {
        var validator = names.validator();

        FieldHelper<MethodSpec> toMethods = validator.newFieldHelper();
        toMethods.userDataType(() -> {
            if (validator instanceof RecordValidator) {
                // Inside the method doesn't call this, and we don't want the user to be able to in order to avoid RobotState issues
                return null;
            }

            return MethodSpec
                    .methodBuilder("to")
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(validator.originalTypeName(), "state")
                    .returns(names.toClassName())
                    .addStatement("return new $T(this.manager, this.targetState, state)", names.toClassName())
                    .build();
        });

        toMethods.permuteFields(FieldHelper.optional)
                .fields((fields, className) -> {
                    if (!(validator instanceof RecordValidator rv)) {
                        throw new UnsupportedOperationException("This method should not have been called with a non-record validator");
                    }

                    // Can't go to an empty list
                    if (className == null) {
                        return null;
                    }

                    boolean hasRobotStateField = fields.stream().anyMatch(f -> f.value().equals(names.robotStateName()));
                    var returnValue = hasRobotStateField ? names.limitedToClassName() : names.toClassName();
                    var callingMethodName = returnValue.equals(names.toClassName()) ? "to" : "limitedTo";

                    MethodSpec.Builder methodBuilder = MethodSpec
                            .methodBuilder("to")
                            .addModifiers(Modifier.PUBLIC)
                            .returns(returnValue);

                    for (var field : fields) {
                        methodBuilder.addParameter(field.value(), field.name());
                    }

                    CodeBlock code = CodeBlock
                            .builder()
                            .add("return $L(\n", callingMethodName)
                            .add(
                                    rv.dataEmitter(fields)
                                            .withConstructor()
                                            .withNestedClassesWrapped()
                                            .emit()
                            )
                            .add(");\n")
                            .build();

                    return methodBuilder.addCode(code).build();
                });

        // For field sets without a robot state, this internal method will be called
        toMethods.wrappedType(() -> MethodSpec
                .methodBuilder("to")
                .addModifiers(Modifier.PRIVATE)
                .returns(names.toClassName())
                .addParameter(validator.wrappedClassName(), "state")
                .addStatement("return new $T(this.manager, this.targetState, state)", names.toClassName())
                .build());

        // For field sets with a robot state, this internal method will be called, which returns the LimitedTo class
        if (validator instanceof RecordValidator rv && rv.robotStatePresent) {
            var method = MethodSpec
                    .methodBuilder("limitedTo")
                    .addModifiers(Modifier.PRIVATE)
                    .returns(names.limitedToClassName())
                    .addParameter(names.dataTypeName(), "state")
                    .addStatement("return new $T(this.manager, this.targetState, state)", names.limitedToClassName())
                    .build();

            toMethods.add(method);
        }

        for(var method : toMethods) {
            builder.addMethod(method);
        }
    }

    private void addTriggerMethods() {
        builder.addMethod(MethodSpec
                .methodBuilder("trigger")
                .addModifiers(Modifier.PUBLIC)
                .returns(Trigger.class)
                .addStatement("return this.trigger($T.getInstance().getDefaultButtonLoop())", CommandScheduler.class)
                .build()
        );

        builder.addMethod(MethodSpec
                .methodBuilder("trigger")
                .addModifiers(Modifier.PUBLIC)
                .returns(Trigger.class)
                .addParameter(EventLoop.class, "eventLoop")
                .addStatement("return manager.trigger(eventLoop, targetState)")
                .build()
        );
    }

    @Override
    public TypeSpec build() {
        return builder.build();
    }
}
