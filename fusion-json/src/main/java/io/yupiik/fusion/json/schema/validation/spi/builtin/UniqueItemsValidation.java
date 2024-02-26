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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class UniqueItemsValidation implements ValidationExtension {
    @Override
    public Optional<Function<Object, Stream<ValidationResult.ValidationError>>> create(final ValidationContext model) {
        return Optional.ofNullable(model.schema().get("uniqueItems"))
                .filter(Boolean.TRUE::equals)
                .map(max -> new Impl(model.toPointer(), model.valueProvider()));
    }

    private static class Impl extends BaseValidation {
        private Impl(final String pointer,
                     final Function<Object, Object> extractor) {
            super(pointer, extractor, TypeFilter.ARRAY);
        }

        @Override
        protected Stream<ValidationResult.ValidationError> onArray(final Collection<?> array) {
            final var uniques = new HashSet<>(array);
            if (array.size() != uniques.size()) {
                final Collection<Object> duplicated = new ArrayList<>(array);
                duplicated.removeAll(uniques);
                return Stream.of(new ValidationResult.ValidationError(pointer, "duplicated items: " + duplicated));
            }
            return Stream.empty();
        }

        @Override
        public String toString() {
            return "UniqueItems{pointer='" + pointer + "'}";
        }
    }
}
