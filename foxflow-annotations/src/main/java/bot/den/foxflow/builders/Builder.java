package bot.den.foxflow.builders;

import bot.den.foxflow.Field;
import bot.den.foxflow.FieldMap;
import com.palantir.javapoet.ClassName;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class Builder<T> implements Iterable<T> {
    private final List<T> results;
    private final List<Field<ClassName>> fields;
    private final Map<List<Field<ClassName>>, ClassName> fieldsToDataClass;
    private final Map<ClassName, List<Field<ClassName>>> dataClassToFields;

    public Builder() {
        this.results = new ArrayList<>();
        this.fields = new ArrayList<>();
        this.fieldsToDataClass = new HashMap<>();
        this.dataClassToFields = new HashMap<>();
    }

    public Builder(List<Field<ClassName>> fields, Map<List<Field<ClassName>>, ClassName> fieldsToDataClass) {
        this.results = new ArrayList<>();
        this.fields = fields;
        this.fieldsToDataClass = fieldsToDataClass;
        this.dataClassToFields = new HashMap<>();
        for(var entry : fieldsToDataClass.entrySet()) {
            this.dataClassToFields.put(entry.getValue(), entry.getKey());
        }
    }

    @Override
    public Iterator<T> iterator() {
        return results.iterator();
    }

    public void userDataType(Supplier<T> supplier) {
        T value = supplier.get();
        this.results.add(value);
    }

    public void wrappedType(Supplier<T> supplier) {
        T value = supplier.get();
        this.results.add(value);
    }

    public <R> FieldBuilder<R> permuteFields(Function<Field<ClassName>, Collection<R>> transformation) {
        return new FieldBuilder<>(transformation);
    }

    public class FieldBuilder<R> {
        private final Function<Field<ClassName>, Collection<R>> transformation;

        FieldBuilder(Function<Field<ClassName>, Collection<R>> transformation) {
            this.transformation = transformation;
        }

        void fields(BiFunction<List<Field<R>>, ClassName, T> function) {
            List<FieldMap<R>> permutations = getPermutationsOfFields();

            for(var fieldMap : permutations) {
                T result = function.apply(fieldMap.fields(), fieldMap.dataClass());
                if(result != null) {
                    results.add(result);
                }
            }
        }

        private List<FieldMap<R>> getPermutationsOfFields() {
            Stack<Field<ClassName>> fieldStack = new Stack<>();
            fieldStack.addAll(fields);

            // Starting list with one empty element as the permutations seed
            List<FieldMap<R>> permutations = new ArrayList<>(List.of());
            while(!fieldStack.isEmpty()) {
                // Get the right most field
                Field<ClassName> field = fieldStack.pop();

                // Transform it so we can iterate over the transformedValues
                var transformedValues = this.transformation.apply(field);

                List<FieldMap<R>> newPermutations = new ArrayList<>();

                for(var permutation : permutations) {
                    var oldDataClass = permutation.dataClass();
                    var oldBaseFields = dataClassToFields.get(oldDataClass);
                    var newBaseFields = new ArrayList<>(oldBaseFields);
                    newBaseFields.add(0, field);
                    var newDataClass = fieldsToDataClass.get(newBaseFields);

                    for(var value : transformedValues) {
                        var newFields = new ArrayList<>(permutation.fields());
                        var dataClass = oldDataClass;

                        // null is a special value meaning "don't include this field"
                        if(value != null) {
                            newFields.add(0, new Field<>(value, field.fieldName()));
                            dataClass = newDataClass;
                        }

                        newPermutations.add(new FieldMap<>(
                                newFields,
                                dataClass
                        ));
                    }
                }

                permutations = newPermutations;
            }
            return permutations;
        }
    }
}
