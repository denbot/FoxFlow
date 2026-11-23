package bot.den.foxflow.builders.classes;

import bot.den.foxflow.builders.Names;
import bot.den.foxflow.builders.TypedBuilder;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;

public class PairBuilder implements TypedBuilder<TypeSpec> {
    private final Names names;
    private final TypeSpec.Builder builder;

    public PairBuilder(Names names) {
        this.names = names;

        builder = TypeSpec.recordBuilder(names.pairName())
                .addModifiers(Modifier.PUBLIC);

        addConstructor();
    }

    private void addConstructor() {
        MethodSpec dataConstructor = MethodSpec
                .constructorBuilder()
                .addParameter(names.dataTypeName(), "a")
                .addParameter(names.dataTypeName(), "b")
                .build();

        builder.recordConstructor(dataConstructor);
    }

    @Override
    public TypeSpec build() {
        return builder.build();
    }
}
