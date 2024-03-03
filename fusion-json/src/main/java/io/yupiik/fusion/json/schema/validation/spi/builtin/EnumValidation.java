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

import io.yupiik.fusion.json.schema.validation.ValidationResult;
import io.yupiik.fusion.json.schema.validation.spi.builtin.type.TypeFilter;
import io.yupiik.fusion.json.schema.validation.spi.ValidationContext;
import io.yupiik.fusion.json.schema.validation.spi.ValidationExtension;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

public class EnumValidation implements ValidationExtension {
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Function<Object, Stream<ValidationResult.ValidationError>>> create(final ValidationContext model) {
        return ofNullable(model.schema().get("enum"))
                .filter(Collection.class::isInstance)
                .map(it -> (Collection<Object>) it)
                .map(values -> new Impl(values, model.valueProvider(), model.toPointer(), Boolean.TRUE.equals(model.schema().get("nullable"))));
    }

    private static class Impl extends BaseValidation {
        private final Collection<Object> valid;
        private final boolean nullable;

        private Impl(final Collection<Object> valid, final Function<Object, Object> extractor, final String pointer, final boolean nullable) {
            super(pointer, extractor, TypeFilter.OBJECT /*ignored*/);
            this.valid = valid;
            this.nullable = nullable;
        }

        @Override
        public Stream<ValidationResult.ValidationError> apply(final Object root) {
            if (root == null) {
                return Stream.empty();
            }
            final var value = extractor.apply(root);
            if (nullable && value == null) {
                return Stream.empty();
            }
            if (valid.contains(value)) {
                return Stream.empty();
            }
            return Stream.of(new ValidationResult.ValidationError(pointer, "Invalid value, got " + value + ", expected: " + valid));
        }

        @Override
        public String toString() {
            return "Enum{valid=" + valid + ", pointer='" + pointer + '\'' + '}';
        }
    }
}
