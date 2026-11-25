package bot.den.foxflow.builders.classes;

import bot.den.foxflow.Generated;
import bot.den.foxflow.builders.HasClassName;
import bot.den.foxflow.builders.TypedBuilder;
import bot.den.foxflow.builders.names.Names;
import bot.den.foxflow.validator.EnumValidator;
import bot.den.foxflow.validator.RecordValidator;
import com.palantir.javapoet.*;

import javax.lang.model.element.Modifier;
import java.util.*;
import java.util.function.Function;

public class EnabledCacheBuilder implements TypedBuilder<TypeSpec>, HasClassName {
    private final Names names;
    private final TypeSpec.Builder builder;

    private final LinkedHashMap<ClassName, String> enabledFields = new LinkedHashMap<>();

    public EnabledCacheBuilder(Names names) {
        this.names = names;

        builder = TypeSpec
                .classBuilder(names.enabledCacheName())
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Generated.class);

        if (names.validator() instanceof RecordValidator rv) {
            for (var innerClassName : rv.fieldToInnerClass.values()) {
                enabledFields.put(innerClassName, innerClassName.simpleName() + "_Enabled");
            }
        }

        addEnableFields();

        addEnableMethod();

        addGenerateMethod();
    }

    private void addEnableFields() {
        for (var fieldName : enabledFields.values()) {
            builder.addField(FieldSpec
                    .builder(boolean.class, fieldName)
                    .addModifiers(Modifier.PRIVATE)
                    .initializer("false")
                    .build()
            );
        }
    }

    private void addEnableMethod() {
        var method = MethodSpec.methodBuilder("enable")
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addParameter(names.dataTypeName(), "data");

        if (names.validator() instanceof EnumValidator) {
            method
                    .addComment("We have no states to enable, but this does make record state machine generation easier")
                    .addStatement("return false");
        } else if (names.validator() instanceof RecordValidator) {
            var caseStatements = enabledFields
                    .entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByValue())
                    .map(entry -> CodeBlock.of(
                            "case $1T _ -> { if(this.$2L) return false; this.$2L = true; }",
                            entry.getKey(),
                            entry.getValue()
                    ))
                    .collect(CodeBlock.joining("\n"));

            method
                    .addCode("""
                                    switch(data) {
                                        $1L
                                    }
                                    return true;
                                    """,
                            caseStatements);
        }

        builder.addMethod(method.build());
    }

    private void addGenerateMethod() {
        var subDataSetType = ParameterizedTypeName.get(
                ClassName.get(Set.class),
                names.dataTypeName()
        );

        var method = MethodSpec.methodBuilder("generate")
                .addModifiers(Modifier.PUBLIC)
                .returns(subDataSetType)
                .addParameter(names.validator().originalTypeName(), "state");

        if (names.validator() instanceof EnumValidator) {
            method
                    .addComment("Enum state machines only ever contain the one state, this does make record state machine generation easier")
                    .addStatement("return Set.of(state)");
        } else if (names.validator() instanceof RecordValidator rv) {
            method
                    .addStatement("$1T result = new $2T<>()", subDataSetType, HashSet.class);

            rv.fields.forEach(
                    (field) -> method.addStatement("$1T $2LField = state.$2L()", field.value(), field.name())
            );

            enabledFields
                    .forEach((innerClassName, fieldName) ->
                            method
                                    .beginControlFlow("if(this.$L)", fieldName)
                                    .addStatement(
                                            "result.add($L)",
                                            rv.dataEmitter(innerClassName)
                                                    .withConstructor()
                                                    .withNestedClassesWrapped()
                                                    .withTransform(f -> f + "Field")
                                                    .emit()
                                    )
                                    .endControlFlow());

            method.addStatement("return result");
        }

        builder.addMethod(method.build());
    }

    @Override
    public TypeSpec build() {
        return builder.build();
    }

    @Override
    public ClassName className() {
        return names.enabledCacheName();
    }
}
