package bot.den.foxflow.builders.classes;

import bot.den.foxflow.builders.Names;
import bot.den.foxflow.builders.TypedBuilder;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import org.wpilib.units.measure.Time;

import javax.lang.model.element.Modifier;

public class TimeBuilder implements TypedBuilder<TypeSpec> {
    private final Names names;
    private final TypeSpec.Builder builder;

    public TimeBuilder(Names names) {
        this.names = names;

        builder = TypeSpec.recordBuilder(names.timeName())
                .addModifiers(Modifier.PUBLIC);

        addConstructor();
    }

    private void addConstructor() {
        MethodSpec dataConstructor = MethodSpec
                .constructorBuilder()
                .addParameter(names.dataTypeName(), "data")
                .addParameter(Time.class, "time")
                .build();

        builder.recordConstructor(dataConstructor);
    }

    @Override
    public TypeSpec build() {
        return builder.build();
    }
}
