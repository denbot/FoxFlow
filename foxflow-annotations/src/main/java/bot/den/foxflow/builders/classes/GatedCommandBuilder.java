package bot.den.foxflow.builders.classes;

import bot.den.foxflow.builders.HasClassName;
import bot.den.foxflow.builders.OnlyWriteOnce;
import bot.den.foxflow.builders.TypedBuilder;
import bot.den.foxflow.builders.features.CommandsV2;
import bot.den.foxflow.builders.features.Features;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.function.BooleanSupplier;

public class GatedCommandBuilder implements TypedBuilder<TypeSpec>, HasClassName, OnlyWriteOnce {
    private final ClassName gatedCommandClassName;
    private final Features features;
    private final TypeSpec.Builder builder;

    public GatedCommandBuilder(
            ClassName gatedCommandClassName,
            Features features
    ) {
        this.gatedCommandClassName = gatedCommandClassName;
        this.features = features;

        this.builder = TypeSpec.classBuilder(gatedCommandClassName)
                .addModifiers(Modifier.PUBLIC);

        addFields();
        addConstructor();

        if (this.features.commandsV2() instanceof CommandsV2 commandsV2) {
            this.addCommandsV2Code(commandsV2);
        }
    }

    private void addFields() {
        builder.addField(FieldSpec
                .builder(BooleanSupplier.class, "inExpectedState")
                .build()
        );

        builder.addField(FieldSpec
                .builder(features.commandType(), "gatedCommand")
                .build()
        );
    }

    private void addConstructor() {
        var constructor = MethodSpec
                .constructorBuilder()
                .addParameter(BooleanSupplier.class, "inExpectedState")
                .addParameter(features.commandType(), "gatedCommand")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("this.inExpectedState = inExpectedState")
                .addStatement("this.gatedCommand = gatedCommand")
                .build();

        builder.addMethod(constructor);
    }

    private void addCommandsV2Code(CommandsV2 commandsV2) {
        builder.superclass(commandsV2.command());

        // initialize
        {
            builder.addMethod(MethodSpec
                    .methodBuilder("initialize")
                    .addModifiers(Modifier.PUBLIC)
                    .addCode("""
                            if(inExpectedState.getAsBoolean()) {
                                this.gatedCommand.initialize();
                            }
                            """)
                    .build()
            );
        }
        // execute
        {
            builder.addMethod(MethodSpec
                    .methodBuilder("execute")
                    .addModifiers(Modifier.PUBLIC)
                    .addCode("""
                            if(inExpectedState.getAsBoolean()) {
                                this.gatedCommand.execute();
                            }
                            """)
                    .build()
            );
        }

        // isFinished
        {
            builder.addMethod(MethodSpec
                    .methodBuilder("isFinished")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(boolean.class)
                    .addCode("""
                            return ! inExpectedState.getAsBoolean() || this.gatedCommand.isFinished();
                            """)
                    .build()
            );
        }

        // end
        {
            builder.addMethod(MethodSpec
                    .methodBuilder("end")
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(boolean.class, "interrupted")
                    .addCode("""
                            this.gatedCommand.end(interrupted);
                            """)
                    .build()
            );
        }
    }

    @Override
    public TypeSpec build() {
        return builder.build();
    }

    @Override
    public ClassName className() {
        return gatedCommandClassName;
    }
}
