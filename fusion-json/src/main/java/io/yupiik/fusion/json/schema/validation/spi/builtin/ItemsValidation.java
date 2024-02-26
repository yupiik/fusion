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
package io.yupiik.fusion.json.schema.validation.spi.builtin;

import io.yupiik.fusion.json.schema.validation.JsonSchemaValidator;
import io.yupiik.fusion.json.schema.validation.JsonSchemaValidatorFactory;
import io.yupiik.fusion.json.schema.validation.ValidationResult;
import io.yupiik.fusion.json.schema.validation.spi.builtin.type.TypeFilter;
import io.yupiik.fusion.json.schema.validation.spi.ValidationContext;
import io.yupiik.fusion.json.schema.validation.spi.ValidationExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Collections.singleton;

public class ItemsValidation implements ValidationExtension {
    private final JsonSchemaValidatorFactory factory;

    public ItemsValidation(final JsonSchemaValidatorFactory factory) {
        this.factory = factory;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Function<Object, Stream<ValidationResult.ValidationError>>> create(final ValidationContext model) {
        return Optional.ofNullable(model.schema().get("items"))
                .map(items -> {
                    if (TypeFilter.OBJECT.test(items)) {
                        final var objectValidator = factory.newInstance((Map<String, Object>) items);
                        return new ItemsValidator(model.toPointer(), model.valueProvider(), singleton(objectValidator));
                    }
                    if (TypeFilter.ARRAY.test(items)) {
                        return new ItemsValidator(model.toPointer(), model.valueProvider(), ((Collection<?>) items).stream()
                                .filter(TypeFilter.OBJECT)
                                .map(it -> factory.newInstance((Map<String, Object>) it))
                                .toList());
                    }
                    return null;
                });
    }

    private static class ItemsValidator extends BaseValidation {
        private final Collection<JsonSchemaValidator> objectValidators;

        private ItemsValidator(final String pointer,
                               final Function<Object, Object> extractor,
                               final Collection<JsonSchemaValidator> objectValidators) {
            super(pointer, extractor, TypeFilter.ARRAY);
            this.objectValidators = objectValidators;
        }

        @Override
        protected Stream<ValidationResult.ValidationError> onArray(final Collection<?> array) {
            Collection<ValidationResult.ValidationError> errors = null;
            int i = 0;
            final var it = array.iterator();
            while (it.hasNext()) {
                final Object value = it.next();
                final Collection<ValidationResult.ValidationError> itemErrors = objectValidators.stream()
                        .flatMap(validator -> validator.apply(value).errors().stream())
                        .toList();
                if (!itemErrors.isEmpty()) {
                    if (errors == null) {
                        errors = new ArrayList<>();
                    }
                    final String suffix = "[" + i + "]";
                    errors.addAll(itemErrors.stream()
                            .map(e -> new ValidationResult.ValidationError(pointer + e.field() + suffix, e.message()))
                            .toList());
                }
                i++;
            }
            return errors == null ? Stream.empty() : errors.stream();
        }

        @Override
        public String toString() {
            return "Items{validators=" + objectValidators + ", pointer='" + pointer + "'}";
        }
    }
}
