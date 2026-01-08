package bot.den.foxflow.validator;

import bot.den.foxflow.*;
import bot.den.foxflow.exceptions.InvalidStateTransition;
import com.palantir.javapoet.*;
import com.palantir.javapoet.TypeSpec.Builder;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecordValidator implements Validator {
    public final List<ClassName> fieldTypes;
    public final Map<ClassName, String> fieldNameMap;
    public final Map<List<ClassName>, ClassName> fieldToInnerClass;
    public final Map<ClassName, List<ClassName>> innerClassToField;
    public final boolean robotStatePresent;
    public final List<TypeSpec> typesToWrite = new ArrayList<>();

    // These contain the mapping between the class the user defined and our data class
    public final Map<ClassName, ClassName> nestedRecords = new HashMap<>();
    public final Map<ClassName, ClassName> nestedInterfaces = new HashMap<>();

    private final Map<ClassName, Boolean> supportsStateTransition;

    private final ClassName originalTypeName;
    private final ClassName wrappedTypeName;
    private final ClassName robotStateName;
    private final ClassName pairName;
    private final List<List<ClassName>> permutations;

    public RecordValidator(Environment environment) {
        var typeElement = environment.element();
        originalTypeName = ClassName.get(typeElement);

        wrappedTypeName = Util.getUniqueClassName(originalTypeName.peerClass(originalTypeName.simpleName() + "Data"));
        pairName = wrappedTypeName.nestedClass("Pair");

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

            nestedRecordValidators.forEach(rv -> {
                nestedRecords.put(rv.originalTypeName, rv.wrappedTypeName);
            });
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

        fieldTypes = validators
                .stream()
                .map(Validator::originalTypeName)
                .toList();

        supportsStateTransition = validators
                .stream()
                .collect(Collectors.toMap(
                        Validator::originalTypeName,
                        Validator::supportsStateTransition
                ));

        fieldNameMap = new HashMap<>();
        for (var component : recordComponents) {
            var typeName = ClassName.get((TypeElement) typeUtils.asElement(component.asType()));
            var variableName = component.getSimpleName().toString();
            fieldNameMap.put(typeName, variableName);
        }

        permutations = getPermutations(fieldTypes);

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
        robotStatePresent = fieldTypes.contains(robotStateName);
        typesToWrite.add(createRecordWrapper());
    }

    /**
     * This method finds all permutations of a given input and maintains the order of the input types in its output.
     * E.g., ["A", "B", "C"] as input would give you these permutations:
     * ["A"], ["B"], ["C"]
     * ["A", "B"], ["B", "C"]
     * ["A", "B", "C"]
     *
     * @param input The list of ClassName's to find all permutations of
     * @return The permutations of all lengths
     */
    private <T> List<List<T>> getPermutations(List<T> input) {
        List<List<T>> permutations = new LinkedList<>();

        for (int numElements = 1; numElements <= input.size(); numElements++) {
            permutations.addAll(getPermutations(input, numElements));
        }

        return permutations;
    }

    private <T> List<List<T>> getPermutations(List<T> input, int length) {
        List<List<T>> permutations = new LinkedList<>();

        int counter = 0;
        int maxCounter = input.size() * length;
        main_loop:
        while (counter < maxCounter) {
            int tempCounter = counter;

            int lastIndex = Integer.MAX_VALUE;
            List<T> permutation = new ArrayList<>(length);

            // From the right side to the left, we're going to fill in our counted values
            for (int i = length - 1; i >= 0; i--) {
                int index = tempCounter % input.size();

                // If we choose a later index in our list, we'd be putting these out of order, so let's avoid this attempt
                // and try again.
                if (index >= lastIndex) {
                    counter++;
                    continue main_loop;
                }

                permutation.add(0, input.get(index));
                lastIndex = index;

                tempCounter = tempCounter / input.size();
            }

            permutations.add(permutation);
            counter++;
        }

        return permutations;
    }

    private CodeBlock commaSeparate(List<CodeBlock> blocks) {
        CodeBlock.Builder result = CodeBlock.builder();
        for (int i = 0; i < blocks.size(); i++) {
            var block = blocks.get(i);
            result.add(block);

            if (i + 1 < blocks.size()) {
                result.add(", ");
            }
        }

        return result.build();
    }

    public CodeBlock emitFieldNames(List<ClassName> fields, Function<String, String> transformFieldName, boolean wrapNestedClasses) {
        return commaSeparate(
                fields
                        .stream()
                        .map(type -> {
                            var fieldName = transformFieldName.apply(fieldNameMap.get(type));

                            if (type.equals(robotStateName)) {
                                return CodeBlock.of("$T.DISABLED", RobotState.class);
                            } else if (wrapNestedClasses && nestedRecords.containsKey(type)) {
                                return CodeBlock.of("$1T.fromRecord($2L)", nestedRecords.get(type), fieldName);
                            } else if (wrapNestedClasses && nestedInterfaces.containsKey(type)) {
                                return CodeBlock.of("$1T.fromRecord($2L)", nestedInterfaces.get(type), fieldName);
                            } else {
                                return CodeBlock.of(fieldName);
                            }
                        })
                        .toList()
        );
    }

    public CodeBlock emitDataClass(List<ClassName> fields) {
        return emitDataClass(fields, Function.identity());
    }

    public CodeBlock emitDataClass(List<ClassName> fields, Function<String, String> transformFieldName) {
        return CodeBlock.builder()
                .add("new $T(", fieldToInnerClass.get(fields))
                .add(emitFieldNames(fields, transformFieldName, true))
                .add(")")
                .build();
    }

    public CodeBlock emitDataClass(ClassName innerClassName, Function<String, String> transformFieldName) {
        return emitDataClass(innerClassToField.get(innerClassName), transformFieldName);
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
    public boolean supportsStateTransition() {
        // A record class supports state transitions only if any of its fields do.
        return supportsStateTransition.values().stream().anyMatch(v -> v);
    }

    @Override
    public <R> List<R> visitTopLevel(Visitor<R> visitor) {
        return Stream.of(
                        visitor.acceptUserDataType(),
                        visitor.acceptFields(this, fieldTypes),
                        visitor.acceptWrapperDataType()
                )
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public <R> List<R> visitPermutations(Visitor<R> visitor) {
        List<R> result = new ArrayList<>();

        result.add(visitor.acceptUserDataType());

        for (var types : permutations) {
            result.add(visitor.acceptFields(this, types));
        }

        result.add(visitor.acceptWrapperDataType());

        return result.stream().filter(Objects::nonNull).collect(Collectors.toList());
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

        // Inner classes that hold subsets of our data for easy passing around and manipulation
        for (List<ClassName> types : permutations) {
            recordInterfaceBuilder.addType(createInnerClass(types));
        }

        // Next up, we need a helper method for each of the original record fields that help us with comparing if states can transition
        {
            for (var entryType : fieldTypes) {
                var dataTypeName = entryType;
                if (nestedRecords.containsKey(entryType)) {
                    dataTypeName = nestedRecords.get(entryType);
                } else if (nestedInterfaces.containsKey(entryType)) {
                    dataTypeName = nestedInterfaces.get(entryType);
                }
                var entryName = fieldNameMap.get(entryType);

                MethodSpec.Builder extractorMethodBuilder = MethodSpec
                        .methodBuilder("get" + Util.ucfirst(entryName))
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(dataTypeName)
                        .addParameter(wrappedTypeName, "data");
                for (List<ClassName> types : permutations) {
                    if (!types.contains(entryType)) {
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
        ClassName allFieldsPresentClass = fieldToInnerClass.get(fieldTypes);

        // getRecord: User record class -> Our data class
        {
            List<CodeBlock> arguments = fieldTypes
                    .stream()
                    .map(cn -> {
                        String fieldName = fieldNameMap.get(cn);
                        if (nestedRecords.containsKey(cn)) {
                            var nestedDataType = nestedRecords.get(cn);
                            return CodeBlock.of("$1T.fromRecord(record.$2L())", nestedDataType, fieldName);
                        } else if (nestedInterfaces.containsKey(cn)) {
                            var nestedDataType = nestedInterfaces.get(cn);
                            return CodeBlock.of("$1T.fromRecord(record.$2L())", nestedDataType, fieldName);
                        }
                        return CodeBlock.of("record.$1L()", fieldName);
                    })
                    .toList();

            MethodSpec fromRecordMethod = MethodSpec
                    .methodBuilder("fromRecord")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(originalTypeName, "record")
                    .returns(wrappedTypeName)
                    .addStatement("return new $1T($2L)", allFieldsPresentClass, commaSeparate(arguments))
                    .build();

            recordInterfaceBuilder.addMethod(fromRecordMethod);
        }

        // toRecord: Our data class -> User record class
        {
            List<CodeBlock> arguments = fieldTypes
                    .stream()
                    .map(cn -> {
                        String fieldName = fieldNameMap.get(cn);
                        if (nestedRecords.containsKey(cn)) {
                            var nestedDataType = nestedRecords.get(cn);
                            return CodeBlock.of("$1T.toRecord(castData.$2L())", nestedDataType, fieldName);
                        } else if (nestedInterfaces.containsKey(cn)) {
                            return CodeBlock.of("castData.$1L().data()", fieldName);
                        }
                        return CodeBlock.of("castData.$1L()", fieldName);
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
                    .addStatement("return new $1T($2L)", originalTypeName, commaSeparate(arguments))
                    .endControlFlow()
                    .addStatement("throw new $1T(\"Should not have tried converting this class to a record, we don't have all the information required\")", RuntimeException.class)
                    .build();

            recordInterfaceBuilder.addMethod(toRecordMethod);
        }

        // Make merge methods for specific concrete types
        for (var types : permutations) {
            ClassName concreteType = fieldToInnerClass.get(types);

            MethodSpec canMergeMethod = MethodSpec
                    .methodBuilder("canMerge")
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .addParameter(concreteType, "data")
                    .returns(boolean.class)
                    .build();

            recordInterfaceBuilder.addMethod(canMergeMethod);

            MethodSpec mergeMethod = MethodSpec
                    .methodBuilder("merge")
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .addParameter(concreteType, "data")
                    .returns(wrappedTypeName)
                    .build();

            recordInterfaceBuilder.addMethod(mergeMethod);
        }

        // We also need ones for the data interface
        {
            MethodSpec.Builder canMergeMethodBuilder = MethodSpec
                    .methodBuilder("canMerge")
                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                    .addParameter(wrappedTypeName, "data")
                    .returns(boolean.class);

            MethodSpec.Builder mergeMethodBuilder = MethodSpec
                    .methodBuilder("merge")
                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                    .addParameter(wrappedTypeName, "data")
                    .returns(wrappedTypeName);

            for (var innerClass : innerClassToField.keySet()) {
                canMergeMethodBuilder.addStatement("if (data instanceof $T s) return canMerge(s)", innerClass);
                mergeMethodBuilder.addStatement("if (data instanceof $T s) return merge(s)", innerClass);
            }

            canMergeMethodBuilder.addStatement("return false");
            mergeMethodBuilder.addStatement("return null");

            recordInterfaceBuilder.addMethod(canMergeMethodBuilder.build());

            recordInterfaceBuilder.addMethod(mergeMethodBuilder.build());
        }

        MethodSpec numElements = MethodSpec
                .methodBuilder("numElements")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(int.class)
                .build();

        recordInterfaceBuilder.addMethod(numElements);

        recordInterfaceBuilder.addType(createPair());

        return recordInterfaceBuilder.build();
    }

    private TypeSpec createInnerClass(List<ClassName> types) {
        MethodSpec.Builder recordConstructor = MethodSpec
                .constructorBuilder();

        List<CodeBlock> attemptTransitions = new ArrayList<>();
        List<CodeBlock> compareTransitions = new ArrayList<>();

        for (ClassName typeName : types) {
            // Add the type parameter to the constructor
            String fieldName = fieldNameMap.get(typeName);
            var dataTypeName = typeName;
            if (nestedRecords.containsKey(typeName)) {
                dataTypeName = nestedRecords.get(typeName);
            } else if (nestedInterfaces.containsKey(typeName)) {
                dataTypeName = nestedInterfaces.get(typeName);
            }

            recordConstructor.addParameter(dataTypeName, fieldName);

            var checkStateTransition = supportsStateTransition.get(typeName);
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

        ClassName nestedName = fieldToInnerClass.get(types);

        Builder innerClass = TypeSpec
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

        // Make merge methods for specific concrete types
        {
            innerClassToField
                    .entrySet()
                    .stream()
                    .sorted(Comparator.comparing(e -> e.getKey().simpleName()))
                    .forEach(entry -> {
                        var otherInnerClass = entry.getKey();
                        Set<ClassName> commonFields = new HashSet<>(entry.getValue());
                        commonFields.retainAll(types);

                        MethodSpec.Builder canMergeMethodBuilder = MethodSpec
                                .methodBuilder("canMerge")
                                .addModifiers(Modifier.PUBLIC)
                                .addParameter(otherInnerClass, "data")
                                .returns(boolean.class);

                        if (commonFields.isEmpty()) {
                            canMergeMethodBuilder.addStatement("return true");
                        } else {
                            CodeBlock code = CodeBlock.join(
                                    commonFields
                                            .stream()
                                            .map(f -> CodeBlock.of("this.$1L.equals(data.$1L)", fieldNameMap.get(f)))
                                            .toList(),
                                    " && "
                            );
                            canMergeMethodBuilder.addStatement("return $1L", code);
                        }

                        innerClass.addMethod(canMergeMethodBuilder.build());

                        MethodSpec.Builder mergeMethodBuilder = MethodSpec
                                .methodBuilder("merge")
                                .addModifiers(Modifier.PUBLIC)
                                .addParameter(otherInnerClass, "data")
                                .returns(wrappedTypeName);

                        Set<ClassName> allFields = new HashSet<>(entry.getValue());
                        allFields.addAll(types);
                        // We have to filter the fieldTypes to make sure we get the list of common fields in order
                        List<ClassName> fieldsCorrectOrder = fieldTypes.stream().filter(allFields::contains).toList();
                        ClassName commonInnerClass = fieldToInnerClass.get(fieldsCorrectOrder);

                        CodeBlock mergeCode = CodeBlock.join(
                                fieldsCorrectOrder
                                        .stream()
                                        .map(f -> entry.getValue().contains(f) ?
                                                CodeBlock.of("data.$1L", fieldNameMap.get(f)) :
                                                CodeBlock.of("this.$1L", fieldNameMap.get(f)))
                                        .toList(),
                                ",\n"
                        );
                        mergeMethodBuilder.addCode("""
                                return new $1T(
                                    $2L
                                );
                                """, commonInnerClass, mergeCode);

                        innerClass.addMethod(mergeMethodBuilder.build());
                    });
        }

        MethodSpec numElements = MethodSpec
                .methodBuilder("numElements")
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addStatement("return $1L", types.size())
                .build();

        innerClass.addMethod(numElements);

        return innerClass.build();
    }

    private TypeSpec createPair() {
        TypeSpec.Builder pairClass = TypeSpec
                .recordBuilder(pairName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        MethodSpec dataConstructor = MethodSpec
                .constructorBuilder()
                .addParameter(wrappedTypeName, "a")
                .addParameter(wrappedTypeName, "b")
                .build();

        pairClass.recordConstructor(dataConstructor);

        if(robotStatePresent) {
            MethodSpec robotStateConstructor = MethodSpec
                    .constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(robotStateName, "a")
                    .addParameter(robotStateName, "b")
                    .addStatement("this(new $1T(a), new $1T(b))", fieldToInnerClass.get(List.of(robotStateName)))
                    .build();

            pairClass.addMethod(robotStateConstructor);
        }

        return pairClass.build();
    }
}
