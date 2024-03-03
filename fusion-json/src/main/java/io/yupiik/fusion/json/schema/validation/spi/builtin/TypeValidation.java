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
import io.yupiik.fusion.json.schema.validation.spi.ValidationContext;
import io.yupiik.fusion.json.schema.validation.spi.ValidationExtension;
import io.yupiik.fusion.json.schema.validation.spi.builtin.type.TypeFilter;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

public class TypeValidation implements ValidationExtension {
    @Override
    public Optional<Function<Object, Stream<ValidationResult.ValidationError>>> create(final ValidationContext model) {
        final var value = model.schema().get("type");
        if (value instanceof String type) {
            return Optional.of(new Impl(model.toPointer(), model.valueProvider(), mapType(type).toArray(TypeFilter[]::new)));
        }
        if (value instanceof Collection<?> list) {
            return Optional.of(new Impl(
                    model.toPointer(), model.valueProvider(),
                    list.stream().flatMap(this::mapType).toArray(TypeFilter[]::new)));
        }
        throw new IllegalArgumentException(value + " is neither an array or string nor a string");
    }

    private Stream<TypeFilter> mapType(final Object value) {
        switch (String.valueOf(value)) {
            case "null":
                return Stream.of(TypeFilter.NULL);
            case "string":
                return Stream.of(TypeFilter.STRING);
            case "number":
            case "integer":
                return Stream.of(TypeFilter.NUMBER);
            case "array":
                return Stream.of(TypeFilter.ARRAY);
            case "boolean":
                return Stream.of(TypeFilter.BOOL);
            case "object":
            default:
                return Stream.of(TypeFilter.OBJECT);
        }
    }

    private static class Impl extends BaseValidation {
        private final Collection<TypeFilter> types;

        private Impl(final String pointer, final Function<Object, Object> extractor, final TypeFilter... types) {
            super(pointer, extractor, types[0] /*ignored anyway*/);
            // note: should we always add NULL? if not it leads to a very weird behavior for partial objects and required fixes it
            this.types = Stream.concat(Stream.of(types), Stream.of(TypeFilter.NULL))
                    .distinct()
                    .sorted(comparing(i -> i.getClass().getSimpleName()))
                    .collect(toList());
        }

        @Override
        public Stream<ValidationResult.ValidationError> apply(final Object root) {
            if (root == null) {
                return Stream.empty();
            }
            final var value = extractor.apply(root);
            if (value == null || types.stream().anyMatch(f -> f.test(value))) {
                return Stream.empty();
            }
            return Stream.of(new ValidationResult.ValidationError(pointer, "Expected " + types + " but got " + value.getClass().getTypeName()));
        }

        @Override
        public String toString() {
            return "Type{type=" + types + ", pointer='" + pointer + '\'' + '}';
        }
    }
}
