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

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class ContainsValidation implements ValidationExtension {
    private final JsonSchemaValidatorFactory factory;

    public ContainsValidation(final JsonSchemaValidatorFactory factory) {
        this.factory = factory;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Function<Object, Stream<ValidationResult.ValidationError>>> create(final ValidationContext model) {
        return Optional.ofNullable(model.schema().get("contains"))
                .filter(TypeFilter.OBJECT)
                .map(it -> new ItemsValidator(model.toPointer(), model.valueProvider(), factory.newInstance((Map<String, Object>) it)));
    }

    private static class ItemsValidator extends BaseValidation {
        private final JsonSchemaValidator validator;

        private ItemsValidator(final String pointer,
                               final Function<Object, Object> extractor,
                               JsonSchemaValidator validator) {
            super(pointer, extractor, TypeFilter.ARRAY);
            this.validator = validator;
        }

        @Override
        protected Stream<ValidationResult.ValidationError> onArray(final Collection<?> array) {
            for (final var value : array) {
                final var itemErrors = validator.apply(value).errors();
                if (itemErrors.isEmpty()) {
                    return Stream.empty();
                }
            }
            return Stream.of(new ValidationResult.ValidationError(pointer, "No item matching the expected schema"));
        }

        @Override
        public String toString() {
            return "Contains{validator=" + validator + ", pointer='" + pointer + "'}";
        }
    }
}
