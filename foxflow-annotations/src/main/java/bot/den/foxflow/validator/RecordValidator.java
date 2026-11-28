package bot.den.foxflow.validator;

import bot.den.foxflow.*;
import bot.den.foxflow.builders.FieldHelper;
import bot.den.foxflow.builders.classes.RecordDataBuilder;
import bot.den.foxflow.exceptions.InvalidSetupException;
import com.palantir.javapoet.*;
import org.wpilib.util.Pair;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RecordValidator implements Validator {
    public final List<Field<ClassName>> fields;
    public final Map<List<Field<ClassName>>, ClassName> fieldToInnerClass;
    public final Map<ClassName, List<Field<ClassName>>> innerClassToField;
    public final boolean robotStatePresent;
    public final List<Pair<String, TypeSpec>> typesToWrite = new ArrayList<>();

    // These contain the mapping between the class the user defined and our data class
    public final Map<ClassName, ClassName> nestedInterfaces = new HashMap<>();
    public final Map<ClassName, Element> defaultValues = new HashMap<>();

    public final Map<ClassName, Boolean> supportsStateTransition;

    private final ClassName originalTypeName;
    private final ClassName wrappedTypeName;

    public RecordValidator(Environment environment) {
        var typeElement = environment.element();
        originalTypeName = ClassName.get(typeElement);

        String obfuscatedPackageName = environment.getObfuscatedPackageName(originalTypeName);
        wrappedTypeName = ClassName.get(obfuscatedPackageName, "Data");

        var typeUtils = environment.processingEnvironment().getTypeUtils();

        var recordComponents = typeElement.getRecordComponents();

        if (recordComponents.isEmpty()) {
            throw new InvalidSetupException("An empty record isn't supported for building a state machine. Failed to build state machine for " + originalTypeName);
        }

        // Validate each enum
        var validators = recordComponents
                .stream()
                .map((e) -> {
                    var type = e.asType();

                    if (typeUtils.asElement(type) instanceof TypeElement element) {
                        var newEnvironment = environment.forNewElement(element);

                        switch(element.getKind()) {
                            case ENUM: return new EnumValidator(newEnvironment);
                            case INTERFACE: return new InterfaceValidator(newEnvironment);
                        }
                    }
                    throw new InvalidSetupException("Invalid type " + type + " for field " + e.getSimpleName() + " in record " + typeElement.getQualifiedName());
                })
                .toList();

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

            interfaceValidators.forEach(iv -> nestedInterfaces.put(iv.originalTypeName(), iv.wrappedClassName()));
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

        // Quick check that we only have one type per field in our record
        {
            Set<ClassName> seen = new HashSet<>();
            Optional<Validator> firstDuplicate = validators
                    .stream()
                    .filter(v -> !seen.add(v.originalTypeName()))
                    .findFirst();

            if(firstDuplicate.isPresent()) {
                throw new InvalidSetupException("Cannot have more than one field of type " + firstDuplicate.get().originalTypeName() + " in record " + originalTypeName);
            }
        }

        supportsStateTransition = validators
                .stream()
                .collect(Collectors.toMap(
                        Validator::originalTypeName,
                        Validator::supportsStateTransition
                ));

        Set<List<Field<ClassName>>> permutations = new PermutationBuilder<>(fields).get();

        fieldToInnerClass = new HashMap<>();
        innerClassToField = new HashMap<>();

        int counter = 0;
        for (var types : permutations) {
            ClassName nestedName = wrappedTypeName.nestedClass("S_" + counter);

            fieldToInnerClass.put(types, nestedName);
            innerClassToField.put(nestedName, types);

            counter++;
        }

        robotStatePresent = fields.stream().anyMatch(f -> f.value().equals(ClassName.get(RobotState.class)));
        typesToWrite.add(new Pair<>(obfuscatedPackageName, new RecordDataBuilder(this).build()));
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
    public boolean supportsStateTransition() {
        // A record class supports state transitions only if any of its fields do.
        return supportsStateTransition.values().stream().anyMatch(v -> v);
    }

    @Override
    public <R> FieldHelper<R> newFieldHelper() {
        return new FieldHelper<>(fields, fieldToInnerClass);
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
