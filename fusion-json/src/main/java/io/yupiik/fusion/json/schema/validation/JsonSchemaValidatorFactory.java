/*
 * Copyright (c) 2022 - present - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.fusion.json.schema.validation;

import io.yupiik.fusion.json.schema.validation.spi.ValidationContext;
import io.yupiik.fusion.json.schema.validation.spi.ValidationExtension;
import io.yupiik.fusion.json.schema.validation.spi.builtin.ContainsValidation;
import io.yupiik.fusion.json.schema.validation.spi.builtin.EnumValidation;
import io.yupiik.fusion.json.schema.validation.spi.builtin.ExclusiveMaximumValidation;
import io.yupiik.fusion.json.schema.validation.spi.builtin.ExclusiveMinimumValidation;
import io.yupiik.fusion.json.schema.validation.spi.builtin.IntegerValidation;
import io.yupiik.fusion.json.schema.validation.spi.builtin.ItemsValidation;
import io.yupiik.fusion.json.schema.validation.spi.builtin.MaxItemsValidation;
import io.yupiik.fusion.json.schema.validation.spi.builtin.MaxLengthValidation;
import io.yupiik.fusion.json.schema.validation.spi.builtin.MaxPropertiesValidation;
import io.yupiik.fusion.json.schema.validation.spi.builtin.MaximumValidation;
import io.yupiik.fusion.json.schema.validation.spi.builtin.MinItemsValidation;
import io.yupiik.fusion.json.schema.validation.spi.builtin.MinLengthValidation;
import io.yupiik.fusion.json.schema.validation.spi.builtin.MinPropertiesValidation;
import io.yupiik.fusion.json.schema.validation.spi.builtin.MinimumValidation;
import io.yupiik.fusion.json.schema.validation.spi.builtin.MultipleOfValidation;
import io.yupiik.fusion.json.schema.validation.spi.builtin.PatternValidation;
import io.yupiik.fusion.json.schema.validation.spi.builtin.RequiredValidation;
import io.yupiik.fusion.json.schema.validation.spi.builtin.TypeValidation;
import io.yupiik.fusion.json.schema.validation.spi.builtin.UniqueItemsValidation;
import io.yupiik.fusion.json.schema.validation.spi.builtin.regex.JavaRegex;
import io.yupiik.fusion.json.schema.validation.spi.builtin.type.TypeFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

public class JsonSchemaValidatorFactory implements AutoCloseable {
    private static final String[] ROOT_PATH = new String[0];
    private static final Function<Object, Stream<ValidationResult.ValidationError>> NO_VALIDATION = new Function<>() {
        @Override
        public Stream<ValidationResult.ValidationError> apply(Object Object) {
            return Stream.empty();
        }

        @Override
        public String toString() {
            return "NoValidation";
        }
    };

    private final List<ValidationExtension> extensions = new ArrayList<>();

    // js is closer to default and actually most used in the industry
    private final AtomicReference<Function<String, Predicate<CharSequence>>> regexFactory = new AtomicReference<>(this::newRegexFactory);

    public JsonSchemaValidatorFactory(final ValidationExtension... customValidations) {
        extensions.addAll(createDefaultValidations());
    }

    // enable to use a javascript impl if people add the megs needed to have an embedded (or not) js runtime
    // but default to something faster
    protected Predicate<CharSequence> newRegexFactory(final String regex) {
        return new JavaRegex(regex);
    }

    // see http://json-schema.org/latest/json-schema-validation.html
    public List<ValidationExtension> createDefaultValidations() {
        return asList(
                new RequiredValidation(),
                new TypeValidation(),
                new IntegerValidation(),
                new EnumValidation(),
                new MultipleOfValidation(),
                new MaximumValidation(),
                new MinimumValidation(),
                new ExclusiveMaximumValidation(),
                new ExclusiveMinimumValidation(),
                new MaxLengthValidation(),
                new MinLengthValidation(),
                new PatternValidation(regexFactory.get()),
                new ItemsValidation(this),
                new MaxItemsValidation(),
                new MinItemsValidation(),
                new UniqueItemsValidation(),
                new ContainsValidation(this),
                new MaxPropertiesValidation(),
                new MinPropertiesValidation()
                // TODO: dependencies, propertyNames, if/then/else, allOf/anyOf/oneOf/not,
                //       format validations
        );
    }

    public JsonSchemaValidatorFactory appendExtensions(final ValidationExtension... extensions) {
        this.extensions.addAll(asList(extensions));
        return this;
    }

    public JsonSchemaValidatorFactory setExtensions(final ValidationExtension... extensions) {
        this.extensions.clear();
        return appendExtensions(extensions);
    }

    public JsonSchemaValidatorFactory setRegexFactory(final Function<String, Predicate<CharSequence>> factory) {
        regexFactory.set(factory);
        return this;
    }

    public JsonSchemaValidator newInstance(final Map<String, Object> schema) {
        return new JsonSchemaValidator(buildValidator(ROOT_PATH, schema, null));
    }

    @Override
    public void close() {
        // no-op for now
    }

    private Function<Object, Stream<ValidationResult.ValidationError>> buildValidator(final String[] path,
                                                                                      final Map<String, Object> schema,
                                                                                      final Function<Object, Object> valueProvider) {
        final List<Function<Object, Stream<ValidationResult.ValidationError>>> directValidations = buildDirectValidations(path, schema, valueProvider).toList();
        final Function<Object, Stream<ValidationResult.ValidationError>> nestedValidations = buildPropertiesValidations(path, schema, valueProvider);
        final Function<Object, Stream<ValidationResult.ValidationError>> dynamicNestedValidations = buildPatternPropertiesValidations(path, schema, valueProvider);
        final Function<Object, Stream<ValidationResult.ValidationError>> fallbackNestedValidations = buildAdditionalPropertiesValidations(path, schema, valueProvider);
        return new ValidationsFunction(
                Stream.concat(
                                directValidations.stream(),
                                Stream.of(nestedValidations, dynamicNestedValidations, fallbackNestedValidations))
                        .collect(toList()));
    }

    private Stream<Function<Object, Stream<ValidationResult.ValidationError>>> buildDirectValidations(final String[] path,
                                                                                                      final Map<String, Object> schema,
                                                                                                      final Function<Object, Object> valueProvider) {
        final var model = new ValidationContext(path, schema, valueProvider);
        return extensions.stream()
                .map(e -> e.create(model))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    @SuppressWarnings("unchecked")
    private Function<Object, Stream<ValidationResult.ValidationError>> buildPropertiesValidations(final String[] path,
                                                                                                  final Map<String, Object> schema,
                                                                                                  final Function<Object, Object> valueProvider) {
        return ofNullable(schema.get("properties"))
                .filter(TypeFilter.OBJECT)
                .map(it -> ((Map<String, Object>) it).entrySet().stream()
                        .filter(e -> TypeFilter.OBJECT.test(e.getValue()))
                        .map(obj -> {
                            final var key = obj.getKey();
                            final var fieldPath = Stream.concat(Stream.of(path), Stream.of(key)).toArray(String[]::new);
                            return buildValidator(fieldPath, (Map<String, Object>) obj.getValue(), new ChainedValueAccessor(valueProvider, key));
                        })
                        .collect(toList()))
                .map(this::toFunction)
                .orElse(NO_VALIDATION);
    }

    // not the best impl but is it really an important case?
    @SuppressWarnings("unchecked")
    private Function<Object, Stream<ValidationResult.ValidationError>> buildPatternPropertiesValidations(final String[] path,
                                                                                                         final Map<String, Object> schema,
                                                                                                         final Function<Object, Object> valueProvider) {
        return ofNullable(schema.get("patternProperties"))
                .filter(TypeFilter.OBJECT)
                .map(it -> ((Map<String, Object>) it).entrySet().stream()
                        .filter(e -> TypeFilter.OBJECT.test(e.getValue()))
                        .map(obj -> {
                            final var pattern = regexFactory.get().apply(obj.getKey());
                            final var currentSchema = (Map<String, Object>) obj.getValue();
                            // no cache cause otherwise it could be in properties
                            return (Function<Object, Stream<ValidationResult.ValidationError>>) root -> {
                                final var validable = Optional.ofNullable(valueProvider)
                                        .map(provider -> provider.apply(root))
                                        .orElse(root);
                                if (!(validable instanceof Map<?, ?> map)) {
                                    return Stream.empty();
                                }
                                return ((Map<String, Object>) map).entrySet().stream()
                                        .filter(e -> pattern.test(e.getKey()))
                                        .flatMap(e -> buildValidator(
                                                Stream.concat(Stream.of(path), Stream.of(e.getKey())).toArray(String[]::new),
                                                currentSchema,
                                                o -> ((Map<String, Object>) o).get(e.getKey()))
                                                .apply(validable));
                            };
                        })
                        .collect(toList()))
                .map(this::toFunction)
                .orElse(NO_VALIDATION);
    }

    @SuppressWarnings("unchecked")
    private Function<Object, Stream<ValidationResult.ValidationError>> buildAdditionalPropertiesValidations(final String[] path,
                                                                                                            final Map<String, Object> schema,
                                                                                                            final Function<Object, Object> valueProvider) {
        return ofNullable(schema.get("additionalProperties"))
                .filter(TypeFilter.OBJECT)
                .map(it -> {
                    Predicate<String> excluded = s -> false;
                    if (schema.containsKey("properties")) {
                        final var properties = ((Map<String, Object>) schema.get("properties")).keySet();
                        excluded = excluded.and(s -> !properties.contains(s));
                    }
                    if (schema.containsKey("patternProperties")) {
                        final var properties = ((Map<String, Object>) schema.get("patternProperties")).keySet().stream()
                                .map(regexFactory.get())
                                .toList();
                        excluded = excluded.and(s -> properties.stream().noneMatch(p -> p.test(s)));
                    }
                    final var excludeAttrRef = excluded;

                    final var currentSchema = (Map<String, Object>) it;
                    return (Function<Object, Stream<ValidationResult.ValidationError>>) validable -> {
                        if (!(validable instanceof Map<?, ?> map)) {
                            return Stream.empty();
                        }

                        final var casted = (Map<String, Object>) map;
                        return casted.entrySet().stream()
                                .filter(e -> excludeAttrRef.test(e.getKey()))
                                .flatMap(e -> buildValidator(
                                        Stream.concat(Stream.of(path), Stream.of(e.getKey())).toArray(String[]::new),
                                        currentSchema,
                                        new ChainedValueAccessor(valueProvider, e.getKey())).apply(validable));
                    };
                })
                .orElse(NO_VALIDATION);
    }

    private Function<Object, Stream<ValidationResult.ValidationError>> toFunction(
            final List<Function<Object, Stream<ValidationResult.ValidationError>>> validations) {
        return new ValidationsFunction(validations);
    }

    private static class ValidationsFunction implements Function<Object, Stream<ValidationResult.ValidationError>> {
        private final List<Function<Object, Stream<ValidationResult.ValidationError>>> delegates;

        private ValidationsFunction(final List<Function<Object, Stream<ValidationResult.ValidationError>>> validations) {
            // unwrap when possible to simplify the stack and make toString readable (debug)
            this.delegates = validations.stream()
                    .flatMap(it -> it instanceof ValidationsFunction v ? v.delegates.stream() : Stream.of(it))
                    .filter(it -> it != NO_VALIDATION)
                    .collect(toList());
        }

        @Override
        public Stream<ValidationResult.ValidationError> apply(final Object Object) {
            return delegates.stream().flatMap(v -> v.apply(Object));
        }

        @Override
        public String toString() {
            return delegates.toString();
        }
    }

    private static class ChainedValueAccessor implements Function<Object, Object> {
        private final Function<Object, Object> parent;
        private final String key;

        private ChainedValueAccessor(final Function<Object, Object> valueProvider, final String key) {
            this.parent = valueProvider;
            this.key = key;
        }

        @Override
        public Object apply(final Object value) {
            if ((parent == null ? value : parent.apply(value)) instanceof Map<?, ?> map) {
                return map.get(key);
            }
            return null;
        }

        @Override
        public String toString() {
            return "ChainedValueAccessor{" +
                    "parent=" + parent +
                    ", key='" + key + '\'' +
                    '}';
        }
    }
}
