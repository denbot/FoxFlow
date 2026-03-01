package bot.den.foxflow.builders.classes;

import bot.den.foxflow.Field;
import bot.den.foxflow.LimitsStateTransitions;
import bot.den.foxflow.Util;
import bot.den.foxflow.builders.TypedBuilder;
import bot.den.foxflow.exceptions.InvalidStateTransition;
import bot.den.foxflow.validator.RecordValidator;
import com.palantir.javapoet.*;
import edu.wpi.first.math.Pair;
import edu.wpi.first.units.measure.Time;

import javax.lang.model.element.Modifier;
import java.util.*;

public class RecordDataBuilder implements TypedBuilder<TypeSpec> {
    private final TypeSpec.Builder builder;
    private final RecordValidator validator;
    private final ClassName allFieldsPresentDataClass;

    private final boolean needsRemoveNulls;
    private final Map<ClassName, List<ClassName>> removeNullsMap = new HashMap<>();

    public RecordDataBuilder(
            RecordValidator validator
    ) {
        this.validator = validator;
        this.builder = TypeSpec
                .interfaceBuilder(validator.wrappedClassName())
                .addModifiers(Modifier.PUBLIC);

        allFieldsPresentDataClass = validator.fieldToInnerClass.get(validator.fields);
        needsRemoveNulls = validator.innerClassToField.get(allFieldsPresentDataClass).size() > 1;
        // We need this in the toData / fromRecord methods

        generateRemoveNullsMap();

        addSuperinterface();
        addCanTransitionHelperMethods();
        addFromRecordMethod();
        addToRecordMethod();
        addCanMergeMethod();
        addMergeMethod();

        addTransitionMethods();
        addNumElementsMethod();

        addPairType();
        addTimeType();
        addNestedTypes();
    }

    private void generateRemoveNullsMap() {
        if (!needsRemoveNulls) {
            return;
        }

        Queue<Pair<ClassName, Integer>> queue = new LinkedList<>();
        queue.add(new Pair<>(allFieldsPresentDataClass, -1));

        while (!queue.isEmpty()) {
            var pair = queue.poll();
            var subDataClass = pair.getFirst();
            var lastRemovedIndex = pair.getSecond();

            List<ClassName> children = new ArrayList<>();

            for (int toRemoveIndex = lastRemovedIndex + 1; toRemoveIndex < validator.fields.size(); toRemoveIndex++) {
                List<Field<ClassName>> fieldsWithRemovedElement = new ArrayList<>(validator.fields);
                //noinspection SuspiciousListRemoveInLoop
                fieldsWithRemovedElement.remove(toRemoveIndex);

                List<Field<ClassName>> subDataFields = new ArrayList<>(validator.innerClassToField.get(subDataClass));
                subDataFields.retainAll(fieldsWithRemovedElement);

                var childSubData = validator.fieldToInnerClass.get(subDataFields);
                children.add(childSubData);

                if (subDataFields.size() > 1 && toRemoveIndex + 1 < validator.fields.size()) {
                    queue.add(new Pair<>(childSubData, toRemoveIndex));
                }
            }

            removeNullsMap.put(subDataClass, children);
        }
    }

    private void addSuperinterface() {
        ParameterizedTypeName limitsStateTransitions = ParameterizedTypeName
                .get(
                        ClassName.get(LimitsStateTransitions.class),
                        validator.wrappedClassName()
                );

        if (validator.supportsStateTransition()) {
            builder.addSuperinterface(limitsStateTransitions);
        }
    }

    private void addCanTransitionHelperMethods() {
        for (var field : validator.fields) {
            var dataTypeName = field.value();
            if (validator.nestedRecords.containsKey(dataTypeName)) {
                dataTypeName = validator.nestedRecords.get(dataTypeName);
            } else if (validator.nestedInterfaces.containsKey(dataTypeName)) {
                dataTypeName = validator.nestedInterfaces.get(dataTypeName);
            }
            var entryName = field.name();

            MethodSpec.Builder extractorMethodBuilder = MethodSpec
                    .methodBuilder("get" + Util.ucfirst(entryName))
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(dataTypeName)
                    .addParameter(validator.wrappedClassName(), "data");
            for (var typesClassEntry : validator.fieldToInnerClass.entrySet()) {
                var types = typesClassEntry.getKey();
                if (!types.contains(field)) {
                    continue;
                }

                var innerClassName = typesClassEntry.getValue();

                extractorMethodBuilder.addStatement(
                        "if (data instanceof $T s) return s." + entryName,
                        innerClassName
                );
            }

            extractorMethodBuilder.addStatement("return null");

            builder.addMethod(extractorMethodBuilder.build());
        }
    }

    /**
     * User record -> our data class
     */
    private void addFromRecordMethod() {
        List<CodeBlock> arguments = validator.fields
                .stream()
                .map(field -> {
                    String name = field.name();
                    ClassName type = field.value();
                    if (validator.nestedRecords.containsKey(type)) {
                        var nestedDataType = validator.nestedRecords.get(type);
                        return CodeBlock.of("$1T.fromRecord(record.$2L())", nestedDataType, name);
                    } else if (validator.nestedInterfaces.containsKey(type)) {
                        var nestedDataType = validator.nestedInterfaces.get(type);
                        return CodeBlock.of("$1T.fromRecord(record.$2L())", nestedDataType, name);
                    }
                    return CodeBlock.of("record.$1L()", name);
                })
                .toList();

        MethodSpec fromRecordMethod = MethodSpec
                .methodBuilder("fromRecord")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(validator.originalTypeName(), "record")
                .returns(validator.wrappedClassName())
                .addStatement("return new $1T($2L)", allFieldsPresentDataClass, CodeBlock.join(arguments, ", "))
                .build();

        builder.addMethod(fromRecordMethod);
    }

    /**
     * Our data class -> user record
     */
    private void addToRecordMethod() {
        List<CodeBlock> arguments = validator.fields
                .stream()
                .map(field -> {
                    String name = field.name();
                    ClassName type = field.value();
                    if (validator.nestedRecords.containsKey(type)) {
                        var nestedDataType = validator.nestedRecords.get(type);
                        return CodeBlock.of("$1T.toRecord(castData.$2L())", nestedDataType, name);
                    } else if (validator.nestedInterfaces.containsKey(type)) {
                        return CodeBlock.of("castData.$1L().data()", name);
                    }
                    return CodeBlock.of("castData.$1L()", name);
                })
                .toList();


        // This should actually start by crashing if it's not the `allFieldsPresentClass`
        // Then it should cast it to a new variable
        MethodSpec toRecordMethod = MethodSpec
                .methodBuilder("toRecord")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(validator.wrappedClassName(), "data")
                .returns(validator.originalTypeName())
                .beginControlFlow("if (data instanceof $T castData)", allFieldsPresentDataClass)
                .addStatement("return new $1T($2L)", validator.originalTypeName(), CodeBlock.join(arguments, ", "))
                .endControlFlow()
                .addStatement("throw new $1T(\"Should not have tried converting this class to a record, we don't have all the information required\")", RuntimeException.class)
                .build();

        builder.addMethod(toRecordMethod);
    }

    private void addCanMergeMethod() {
        MethodSpec.Builder canMergeMethodBuilder = MethodSpec
                .methodBuilder("canMerge")
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .addParameter(validator.wrappedClassName(), "data")
                .returns(boolean.class)
                .addCode(CodeBlock.join(
                        validator.fields.stream()
                                .map(f -> CodeBlock.of("var this_$1L = get$2L(this);\nvar data_$1L = get$2L(data);", f.name(), Util.ucfirst(f.name())))
                                .toList(),
                        "\n"
                ))
                .addStatement("\nreturn $1L",
                        CodeBlock.join(
                                validator.fields.stream()
                                        .map(f -> CodeBlock.of("(this_$1L == null || data_$1L == null || this_$1L.equals(data_$1L))", f.name()))
                                        .toList(),
                                " && \n")
                );

        builder.addMethod(canMergeMethodBuilder.build());
    }

    private void addMergeMethod() {
        {
            MethodSpec mergeMethodBuilder = MethodSpec
                    .methodBuilder("merge")
                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                    .addParameter(validator.wrappedClassName(), "data")
                    .returns(validator.wrappedClassName())
                    .addCode(CodeBlock.join(
                            validator.fields.stream()
                                    .map(f -> CodeBlock.of("var this_$1L = get$2L(this);\nvar data_$1L = get$2L(data);", f.name(), Util.ucfirst(f.name())))
                                    .toList(),
                            "\n"
                    ))
                    .addCode("""
                                    \n
                                    return $1L$2L;
                                    """,
                            validator.dataEmitter(allFieldsPresentDataClass)
                                    .withConstructor()
                                    .withTransform("this_%1$s == null ? data_%1$s : this_%1$s"::formatted)
                                    .emit(),
                            needsRemoveNulls ? ".removeNulls()" : ""
                    ).build();

            builder.addMethod(mergeMethodBuilder);
        }
    }

    private void addTransitionMethods() {
        List<CodeBlock> attemptTransitions = new ArrayList<>();
        List<CodeBlock> compareTransitions = new ArrayList<>();

        for (var field : validator.fields) {
            // Add the type parameter to the constructor
            String fieldName = field.name();
            var dataTypeName = field.value();
            if (validator.nestedRecords.containsKey(dataTypeName)) {
                dataTypeName = validator.nestedRecords.get(dataTypeName);
            } else if (validator.nestedInterfaces.containsKey(dataTypeName)) {
                dataTypeName = validator.nestedInterfaces.get(dataTypeName);
            }

            var checkStateTransition = validator.supportsStateTransition.get(field.value());
            if (checkStateTransition) {
                compareTransitions.add(CodeBlock.of(
                        """
                                $2T this$1LField = get$1L(this);
                                $2T other$1LField = get$1L(data);
                                if(this$1LField != null && other$1LField != null && !this$1LField.canTransitionState(other$1LField)) return false;
                                """,
                        Util.ucfirst(fieldName),
                        dataTypeName
                ));

                attemptTransitions.add(CodeBlock.of(
                        """
                                $2T this$1LField = get$1L(this);
                                $2T other$1LField = get$1L(data);
                                if(this$1LField != null && other$1LField != null) this$1LField.attemptTransitionTo(other$1LField);
                                """,
                        Util.ucfirst(fieldName),
                        dataTypeName
                ));
            }
        }

        if (validator.supportsStateTransition()) {
            MethodSpec canTransitionState = MethodSpec
                    .methodBuilder("canTransitionState")
                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                    .returns(boolean.class)
                    .addParameter(validator.wrappedClassName(), "data")
                    .addCode(compareTransitions.stream().collect(CodeBlock.joining("\n")))
                    .addStatement("return true")
                    .build();

            builder.addMethod(canTransitionState);
        }

        // No point in even adding the method if it's just going to be an empty try statement
        if (!attemptTransitions.isEmpty()) {
            // attemptTransitionTo override
            MethodSpec attemptTransitionTo = MethodSpec
                    .methodBuilder("attemptTransitionTo")
                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                    .addParameter(validator.wrappedClassName(), "data")
                    .beginControlFlow("try")
                    .addCode(attemptTransitions.stream().collect(CodeBlock.joining("\n")))
                    .nextControlFlow("catch ($T ex)", InvalidStateTransition.class)
                    .addStatement("throw new $T(this, data, ex)", InvalidStateTransition.class)
                    .endControlFlow()
                    .build();

            builder.addMethod(attemptTransitionTo);
        }
    }

    private void addNumElementsMethod() {
        builder.addMethod(MethodSpec
                .methodBuilder("numElements")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(int.class)
                .build());
    }

    private void addPairType() {
        MethodSpec dataConstructor = MethodSpec
                .constructorBuilder()
                .addParameter(validator.wrappedClassName(), "a")
                .addParameter(validator.wrappedClassName(), "b")
                .build();

        builder.addType(TypeSpec
                .recordBuilder(validator.pairClassName())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .recordConstructor(dataConstructor)
                .build());
    }

    private void addTimeType() {
        MethodSpec dataConstructor = MethodSpec
                .constructorBuilder()
                .addParameter(validator.wrappedClassName(), "data")
                .addParameter(Time.class, "time")
                .build();

        builder.addType(TypeSpec
                .recordBuilder(validator.timeClassName())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .recordConstructor(dataConstructor)
                .build());
    }

    private void addNestedTypes() {
        for (var types : validator.fieldToInnerClass.keySet()) {
            ClassName nestedName = validator.fieldToInnerClass.get(types);
            builder.addType(createInnerClass(nestedName, removeNullsMap));
        }
    }

    private TypeSpec createInnerClass(ClassName nestedName, Map<ClassName, List<ClassName>> removeNullsMap) {
        MethodSpec.Builder recordConstructor = MethodSpec
                .constructorBuilder();

        var types = validator.innerClassToField.get(nestedName);

        for (var field : types) {
            // Add the type parameter to the constructor
            String fieldName = field.name();
            var dataTypeName = field.value();
            if (validator.nestedRecords.containsKey(dataTypeName)) {
                dataTypeName = validator.nestedRecords.get(dataTypeName);
            } else if (validator.nestedInterfaces.containsKey(dataTypeName)) {
                dataTypeName = validator.nestedInterfaces.get(dataTypeName);
            }

            recordConstructor.addParameter(dataTypeName, fieldName);
        }

        MethodSpec numElements = MethodSpec
                .methodBuilder("numElements")
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addStatement("return $1L", types.size())
                .build();

        TypeSpec.Builder innerClass = TypeSpec
                .recordBuilder(nestedName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .recordConstructor(recordConstructor.build())
                .addSuperinterface(validator.wrappedClassName())
                .addMethod(numElements);

        if (removeNullsMap.containsKey(nestedName)) {
            MethodSpec.Builder removeNullsMethod = MethodSpec
                    .methodBuilder("removeNulls")
                    .returns(validator.wrappedClassName());

            var removeNulls = removeNullsMap.get(nestedName);

            for (ClassName childClass : removeNulls) {
                var fieldDifference = new ArrayList<>(validator.innerClassToField.get(nestedName));
                fieldDifference.removeAll(validator.innerClassToField.get(childClass));

                // Field difference only has one element now by definition of the parent/child relationship
                var field = fieldDifference.get(0);
                boolean needsRemoveNulls = removeNullsMap.containsKey(childClass);

                removeNullsMethod.addStatement(
                        "if(this.$1L == null) return $2L$3L",
                        field.name(),
                        validator.dataEmitter(childClass).withConstructor().emit(),
                        needsRemoveNulls ? ".removeNulls()" : ""
                );
            }

            removeNullsMethod.addStatement("return this");

            innerClass.addMethod(removeNullsMethod.build());
        }

        return innerClass.build();
    }

    @Override
    public TypeSpec build() {
        return builder.build();
    }
}
