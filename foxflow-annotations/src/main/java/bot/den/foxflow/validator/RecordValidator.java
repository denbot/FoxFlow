package bot.den.foxflow.validator;

import bot.den.foxflow.*;
import bot.den.foxflow.builders.Builder;
import bot.den.foxflow.exceptions.InvalidStateTransition;
import com.palantir.javapoet.*;
import edu.wpi.first.math.Pair;
import edu.wpi.first.units.measure.Time;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RecordValidator implements Validator {
    public final List<Field<ClassName>> fields;
    public final Map<List<Field<ClassName>>, ClassName> fieldToInnerClass;
    public final Map<ClassName, List<Field<ClassName>>> innerClassToField;
    public final boolean robotStatePresent;
    public final List<TypeSpec> typesToWrite = new ArrayList<>();

    // These contain the mapping between the class the user defined and our data class
    public final Map<ClassName, ClassName> nestedRecords = new HashMap<>();
    public final Map<ClassName, ClassName> nestedInterfaces = new HashMap<>();
    public final Map<ClassName, Element> defaultValues = new HashMap<>();

    private final Map<ClassName, Boolean> supportsStateTransition;

    private final ClassName originalTypeName;
    private final ClassName wrappedTypeName;
    private final ClassName robotStateName;
    private final ClassName pairName;
    private final ClassName timeName;
    private final Set<List<Field<ClassName>>> permutations;

    public RecordValidator(Environment environment) {
        var typeElement = environment.element();
        originalTypeName = ClassName.get(typeElement);

        wrappedTypeName = Util.getUniqueClassName(originalTypeName.peerClass(originalTypeName.simpleName() + "Data"));
        pairName = wrappedTypeName.nestedClass("Pair");
        timeName = wrappedTypeName.nestedClass("Time");

        var typeUtils = environment.processingEnvironment().getTypeUtils();

        var recordComponents = typeElement.getRecordComponents();

        if (recordComponents.isEmpty()) {
            throw new RuntimeException("An empty record isn't supported for building a state machine. Failed to build state machine for " + originalTypeName);
        }

        // Validate each enum
        var validators = recordComponents
                .stream()
                .map((e) -> {
                    var element = (TypeElement) typeUtils.asElement(e.asType());
                    var newEnvironment = environment.forNewElement(element);

                    if (element.getKind() == ElementKind.ENUM) {
                        return new EnumValidator(newEnvironment);
                    } else if (element.getKind() == ElementKind.RECORD) {
                        // Nested record, we have to go deeper!
                        return new RecordValidator(newEnvironment);
                    } else if (element.getKind() == ElementKind.INTERFACE) {
                        return new InterfaceValidator(newEnvironment);
                    } else {
                        throw new RuntimeException("Invalid type " + element.getSimpleName() + " in record " + typeElement.getSimpleName());
                    }
                })
                .toList();

        // Nested Records
        {
            List<RecordValidator> nestedRecordValidators = validators
                    .stream()
                    .filter(v -> v instanceof RecordValidator)
                    .map(v -> (RecordValidator) v)
                    .toList();

            typesToWrite.addAll(
                    nestedRecordValidators
                            .stream()
                            .flatMap(v -> v.typesToWrite.stream())
                            .toList()
            );

            nestedRecordValidators.forEach(rv -> nestedRecords.put(rv.originalTypeName, rv.wrappedTypeName));
        }

        // Nested Interfaces
        {
            List<InterfaceValidator> interfaceValidators = validators
                    .stream()
                    .filter(v -> v instanceof InterfaceValidator)
                    .map(v -> (InterfaceValidator) v)
                    .toList();

            typesToWrite.addAll(
                    interfaceValidators
                            .stream()
                            .flatMap(v -> v.typesToWrite.stream())
                            .toList()
            );

            interfaceValidators.forEach(iv -> {
                nestedInterfaces.put(iv.originalTypeName(), iv.wrappedClassName());
            });
        }

        // Enum default values
        {
            List<EnumValidator> enumValidators = validators
                    .stream()
                    .filter(v -> v instanceof EnumValidator)
                    .map(v -> (EnumValidator) v)
                    .toList();

            enumValidators.forEach(enumValidator -> {
                if (enumValidator.defaultOption != null) {
                    defaultValues.put(enumValidator.originalTypeName(), enumValidator.defaultOption);
                }
            });
        }

        fields = new ArrayList<>();
        for (var component : recordComponents) {
            var typeName = ClassName.get((TypeElement) typeUtils.asElement(component.asType()));
            var variableName = component.getSimpleName().toString();
            fields.add(new Field<>(typeName, variableName));
        }

        supportsStateTransition = validators
                .stream()
                .collect(Collectors.toMap(
                        Validator::originalTypeName,
                        Validator::supportsStateTransition
                ));

        permutations = new PermutationBuilder<>(fields).get();

        fieldToInnerClass = new HashMap<>();
        innerClassToField = new HashMap<>();

        int counter = 0;
        for (var types : permutations) {
            ClassName nestedName = wrappedTypeName.nestedClass("S_" + counter);

            fieldToInnerClass.put(types, nestedName);
            innerClassToField.put(nestedName, types);

            counter++;
        }

        robotStateName = ClassName.get(RobotState.class);
        robotStatePresent = fields.stream().anyMatch(f -> f.value().equals(robotStateName));
        typesToWrite.add(createRecordWrapper());
    }

    public DataEmitter dataEmitter(List<Field<ClassName>> fields) {
        return new DataEmitter(fields);
    }

    public DataEmitter dataEmitter(ClassName dataClass) {
        return new DataEmitter(innerClassToField.get(dataClass));
    }

    @Override
    public ClassName originalTypeName() {
        return originalTypeName;
    }

    @Override
    public ClassName wrappedClassName() {
        return wrappedTypeName;
    }

    @Override
    public ClassName pairClassName() {
        return pairName;
    }

    @Override
    public TypeName timeClassName() {
        return timeName;
    }

    @Override
    public boolean supportsStateTransition() {
        // A record class supports state transitions only if any of its fields do.
        return supportsStateTransition.values().stream().anyMatch(v -> v);
    }

    @Override
    public <R> bot.den.foxflow.builders.Builder<R> newBuilder() {
        return new Builder<>(fields, fieldToInnerClass);
    }

    private TypeSpec createRecordWrapper() {
        ParameterizedTypeName limitsStateTransitions = ParameterizedTypeName
                .get(
                        ClassName.get(LimitsStateTransitions.class),
                        wrappedTypeName
                );

        /*
         We're going to build a new wrapper around this interface class that is itself a bunch of records that
         implement that interface.
        */
        TypeSpec.Builder recordInterfaceBuilder = TypeSpec
                .interfaceBuilder(wrappedTypeName);

        if (supportsStateTransition()) {
            recordInterfaceBuilder.addSuperinterface(limitsStateTransitions);
        }

        // Next up, we need a helper method for each of the original record fields that help us with comparing if states can transition
        {
            for (var field : fields) {
                var dataTypeName = field.value();
                if (nestedRecords.containsKey(dataTypeName)) {
                    dataTypeName = nestedRecords.get(dataTypeName);
                } else if (nestedInterfaces.containsKey(dataTypeName)) {
                    dataTypeName = nestedInterfaces.get(dataTypeName);
                }
                var entryName = field.name();

                MethodSpec.Builder extractorMethodBuilder = MethodSpec
                        .methodBuilder("get" + Util.ucfirst(entryName))
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(dataTypeName)
                        .addParameter(wrappedTypeName, "data");
                for (List<Field<ClassName>> types : permutations) {
                    if (!types.contains(field)) {
                        continue;
                    }

                    var innerClassName = fieldToInnerClass.get(types);

                    extractorMethodBuilder.addStatement(
                            "if (data instanceof $T s) return s." + entryName,
                            innerClassName
                    );
                }

                extractorMethodBuilder.addStatement("return null");

                recordInterfaceBuilder.addMethod(extractorMethodBuilder.build());
            }
        }

        // We need this in the toData / fromRecord classes
        ClassName allFieldsPresentClass = fieldToInnerClass.get(fields);

        // getRecord: User record class -> Our data class
        {
            List<CodeBlock> arguments = fields
                    .stream()
                    .map(field -> {
                        String name = field.name();
                        ClassName type = field.value();
                        if (nestedRecords.containsKey(type)) {
                            var nestedDataType = nestedRecords.get(type);
                            return CodeBlock.of("$1T.fromRecord(record.$2L())", nestedDataType, name);
                        } else if (nestedInterfaces.containsKey(type)) {
                            var nestedDataType = nestedInterfaces.get(type);
                            return CodeBlock.of("$1T.fromRecord(record.$2L())", nestedDataType, name);
                        }
                        return CodeBlock.of("record.$1L()", name);
                    })
                    .toList();

            MethodSpec fromRecordMethod = MethodSpec
                    .methodBuilder("fromRecord")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(originalTypeName, "record")
                    .returns(wrappedTypeName)
                    .addStatement("return new $1T($2L)", allFieldsPresentClass, CodeBlock.join(arguments, ", "))
                    .build();

            recordInterfaceBuilder.addMethod(fromRecordMethod);
        }

        // toRecord: Our data class -> User record class
        {
            List<CodeBlock> arguments = fields
                    .stream()
                    .map(field -> {
                        String name = field.name();
                        ClassName type = field.value();
                        if (nestedRecords.containsKey(type)) {
                            var nestedDataType = nestedRecords.get(type);
                            return CodeBlock.of("$1T.toRecord(castData.$2L())", nestedDataType, name);
                        } else if (nestedInterfaces.containsKey(type)) {
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
                    .addParameter(wrappedTypeName, "data")
                    .returns(originalTypeName)
                    .beginControlFlow("if (data instanceof $T castData)", allFieldsPresentClass)
                    .addStatement("return new $1T($2L)", originalTypeName, CodeBlock.join(arguments, ", "))
                    .endControlFlow()
                    .addStatement("throw new $1T(\"Should not have tried converting this class to a record, we don't have all the information required\")", RuntimeException.class)
                    .build();

            recordInterfaceBuilder.addMethod(toRecordMethod);
        }

        // canMerge any two data interfaces
        {
            MethodSpec.Builder canMergeMethodBuilder = MethodSpec
                    .methodBuilder("canMerge")
                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                    .addParameter(wrappedTypeName, "data")
                    .returns(boolean.class)
                    .addCode(CodeBlock.join(
                            fields.stream()
                                    .map(f -> CodeBlock.of("var this_$1L = get$2L(this);\nvar data_$1L = get$2L(data);", f.name(), Util.ucfirst(f.name())))
                                    .toList(),
                            "\n"
                    ))
                    .addStatement("\nreturn $1L",
                            CodeBlock.join(
                                    fields.stream()
                                            .map(f -> CodeBlock.of("(this_$1L == null || data_$1L == null || this_$1L.equals(data_$1L))", f.name()))
                                            .toList(),
                                    " && \n")
                    );

            recordInterfaceBuilder.addMethod(canMergeMethodBuilder.build());
        }

        // Merge any two data interfaces
        var topLevelDataSubclass = fieldToInnerClass.get(fields);
        boolean needsRemoveNulls = innerClassToField.get(topLevelDataSubclass).size() > 1;
        {
            MethodSpec mergeMethodBuilder = MethodSpec
                    .methodBuilder("merge")
                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                    .addParameter(wrappedTypeName, "data")
                    .returns(wrappedTypeName)
                    .addCode(CodeBlock.join(
                            fields.stream()
                                    .map(f -> CodeBlock.of("var this_$1L = get$2L(this);\nvar data_$1L = get$2L(data);", f.name(), Util.ucfirst(f.name())))
                                    .toList(),
                            "\n"
                    ))
                    .addCode("""
                            \n
                            return $1L$2L;
                            """,
                            dataEmitter(topLevelDataSubclass)
                                    .withConstructor()
                                    .withTransform("this_%1$s == null ? data_%1$s : this_%1$s"::formatted)
                                    .emit(),
                            needsRemoveNulls ? ".removeNulls()" : ""
                    ).build();

            recordInterfaceBuilder.addMethod(mergeMethodBuilder);
        }

        // removeNulls for the required data types
        Map<ClassName, List<ClassName>> removeNullsMap = new HashMap<>();
        if(needsRemoveNulls) {
            Queue<Pair<ClassName, Integer>> queue = new LinkedList<>();
            queue.add(new Pair<>(topLevelDataSubclass, -1));

            while(!queue.isEmpty()) {
                var pair = queue.poll();
                var subDataClass = pair.getFirst();
                var lastRemovedIndex = pair.getSecond();

                List<ClassName> children = new ArrayList<>();

                for (int toRemoveIndex = lastRemovedIndex + 1; toRemoveIndex < fields.size(); toRemoveIndex++) {
                    List<Field<ClassName>> fieldsWithRemovedElement = new ArrayList<>(fields);
                    fieldsWithRemovedElement.remove(toRemoveIndex);

                    List<Field<ClassName>> subDataFields = new ArrayList<>(innerClassToField.get(subDataClass));
                    subDataFields.retainAll(fieldsWithRemovedElement);

                    var childSubData = fieldToInnerClass.get(subDataFields);
                    children.add(childSubData);

                    if(subDataFields.size() > 1 && toRemoveIndex + 1 < fields.size()) {
                        queue.add(new Pair<>(childSubData, toRemoveIndex));
                    }
                }

                removeNullsMap.put(subDataClass, children);
            }
        }

        // Inner classes that hold subsets of our data for easy passing around and manipulation
        for (var types : permutations) {
            ClassName nestedName = fieldToInnerClass.get(types);
            recordInterfaceBuilder.addType(createInnerClass(nestedName, removeNullsMap.get(nestedName)));
        }

        MethodSpec numElements = MethodSpec
                .methodBuilder("numElements")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(int.class)
                .build();

        recordInterfaceBuilder.addMethod(numElements);

        recordInterfaceBuilder.addType(createPair());
        recordInterfaceBuilder.addType(createTime());

        return recordInterfaceBuilder.build();
    }

    private TypeSpec createInnerClass(ClassName nestedName, List<ClassName> removeNulls) {
        MethodSpec.Builder recordConstructor = MethodSpec
                .constructorBuilder();

        List<CodeBlock> attemptTransitions = new ArrayList<>();
        List<CodeBlock> compareTransitions = new ArrayList<>();

        var types = innerClassToField.get(nestedName);

        for (var field : types) {
            // Add the type parameter to the constructor
            String fieldName = field.name();
            var dataTypeName = field.value();
            if (nestedRecords.containsKey(dataTypeName)) {
                dataTypeName = nestedRecords.get(dataTypeName);
            } else if (nestedInterfaces.containsKey(dataTypeName)) {
                dataTypeName = nestedInterfaces.get(dataTypeName);
            }

            recordConstructor.addParameter(dataTypeName, fieldName);

            var checkStateTransition = supportsStateTransition.get(field.value());
            if (checkStateTransition) {
                compareTransitions.add(CodeBlock.of(
                        """
                                $3T $1LField = $2L(data);
                                if($1LField != null && !this.$1L.canTransitionState($1LField)) return false;
                                """,
                        fieldName,
                        "get" + Util.ucfirst(fieldName),
                        dataTypeName
                ));

                attemptTransitions.add(CodeBlock.of(
                        """
                                $3T $1LField = $2L(data);
                                if($1LField != null) this.$1L.attemptTransitionTo($1LField);
                                """,
                        fieldName,
                        "get" + Util.ucfirst(fieldName),
                        dataTypeName
                ));
            }
        }

        TypeSpec.Builder innerClass = TypeSpec
                .recordBuilder(nestedName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .recordConstructor(recordConstructor.build())
                .addSuperinterface(wrappedTypeName);

        if (supportsStateTransition()) {
            MethodSpec canTransitionState = MethodSpec
                    .methodBuilder("canTransitionState")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(boolean.class)
                    .addParameter(wrappedTypeName, "data")
                    .addCode(compareTransitions.stream().collect(CodeBlock.joining("\n")))
                    .addStatement("return true")
                    .build();

            innerClass.addMethod(canTransitionState);
        }

        // No point in even adding the method if it's just going to be an empty try statement
        if (!attemptTransitions.isEmpty()) {
            // attemptTransitionTo override
            MethodSpec attemptTransitionTo = MethodSpec
                    .methodBuilder("attemptTransitionTo")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(wrappedTypeName, "data")
                    .beginControlFlow("try")
                    .addCode(attemptTransitions.stream().collect(CodeBlock.joining("\n")))
                    .nextControlFlow("catch ($T ex)", InvalidStateTransition.class)
                    .addStatement("throw new $T(this, data, ex)", InvalidStateTransition.class)
                    .endControlFlow()
                    .build();

            innerClass.addMethod(attemptTransitionTo);
        }

        MethodSpec numElements = MethodSpec
                .methodBuilder("numElements")
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addStatement("return $1L", types.size())
                .build();

        innerClass.addMethod(numElements);

        if(removeNulls != null) {
            MethodSpec.Builder removeNullsMethod = MethodSpec
                    .methodBuilder("removeNulls")
                    .returns(wrappedTypeName);

            for (ClassName childClass : removeNulls) {
                var fieldDifference = new ArrayList<>(innerClassToField.get(nestedName));
                fieldDifference.removeAll(innerClassToField.get(childClass));

                // Field difference only has one element now by definition of the parent/child relationship
                var field = fieldDifference.get(0);

                removeNullsMethod.addStatement(
                        "if(this.$1L == null) return $2L",
                        field.name(),
                        dataEmitter(childClass).withConstructor().emit()
                );
            }

            removeNullsMethod.addStatement("return this");

            innerClass.addMethod(removeNullsMethod.build());
        }

        return innerClass.build();
    }

    private TypeSpec createPair() {
        MethodSpec dataConstructor = MethodSpec
                .constructorBuilder()
                .addParameter(wrappedTypeName, "a")
                .addParameter(wrappedTypeName, "b")
                .build();

        return TypeSpec
                .recordBuilder(pairName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .recordConstructor(dataConstructor)
                .build();
    }

    private TypeSpec createTime() {
        MethodSpec dataConstructor = MethodSpec
                .constructorBuilder()
                .addParameter(wrappedTypeName, "data")
                .addParameter(Time.class, "time")
                .build();

        return TypeSpec
                .recordBuilder(timeName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .recordConstructor(dataConstructor)
                .build();
    }

    public class DataEmitter {
        private final List<Field<ClassName>> fields;
        private final ClassName dataClass;
        private boolean emitConstructor = false;
        private Set<ClassName> substituteDefaults = new HashSet<>();
        private boolean wrapNestedClasses = false;

        private Function<String, String> transformFieldName = Function.identity();

        DataEmitter(List<Field<ClassName>> fields) {
            this.fields = fields;
            this.dataClass = fieldToInnerClass.get(fields);
        }

        public DataEmitter withConstructor() {
            this.emitConstructor = true;
            return this;
        }

        public DataEmitter withTransform(Function<String, String> transform) {
            this.transformFieldName = transform;
            return this;
        }

        public DataEmitter withDefaultsSubstituted(Set<ClassName> fields) {
            this.substituteDefaults = fields;
            return this;
        }

        public DataEmitter withNestedClassesWrapped() {
            this.wrapNestedClasses = true;
            return this;
        }

        public CodeBlock emit() {
            CodeBlock.Builder code = CodeBlock.builder();

            if (emitConstructor) {
                code.add("new $T(", this.dataClass);
            }

            List<CodeBlock> fieldCodes = this.fields
                    .stream()
                    .map(field -> {
                        var fieldName = transformFieldName.apply(field.name());

                        var type = field.value();

                        if (substituteDefaults.contains(type) && defaultValues.containsKey(type)) {
                            Element defaultValue = defaultValues.get(type);
                            return CodeBlock.of("$T.$L", defaultValue.getEnclosingElement(), defaultValue);
                        } else if (wrapNestedClasses && nestedRecords.containsKey(type)) {
                            return CodeBlock.of("$1T.fromRecord($2L)", nestedRecords.get(type), fieldName);
                        } else if (wrapNestedClasses && nestedInterfaces.containsKey(type)) {
                            return CodeBlock.of("$1T.fromRecord($2L)", nestedInterfaces.get(type), fieldName);
                        } else {
                            return CodeBlock.of(fieldName);
                        }
                    })
                    .toList();

            code.add(CodeBlock.join(fieldCodes, ", "));

            if (emitConstructor) {
                code.add(")");
            }

            return code.build();
        }
    }
}
