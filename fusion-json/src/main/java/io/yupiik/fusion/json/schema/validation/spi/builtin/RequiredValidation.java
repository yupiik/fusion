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
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toSet;

public class RequiredValidation implements ValidationExtension {
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Function<Object, Stream<ValidationResult.ValidationError>>> create(final ValidationContext model) {
        return ofNullable(model.schema().get("required"))
                .filter(Collection.class::isInstance)
                .map(it -> (Collection<Object>) it)
                .filter(arr -> arr.stream().allMatch(TypeFilter.STRING))
                .map(arr -> arr.stream().map(Object::toString).collect(toSet()))
                .map(required -> new Impl(required, model.valueProvider(), model.toPointer()));
    }

    private static class Impl extends BaseValidation {
        private final Collection<String> required;

        private Impl(final Collection<String> required, final Function<Object, Object> extractor, final String pointer) {
            super(pointer, extractor, TypeFilter.OBJECT);
            this.required = required;
        }

        @Override
        public Stream<ValidationResult.ValidationError> onObject(final Map<String, Object> obj) {
            if (obj == null) {
                return toErrors(required.stream());
            }
            return toErrors(required.stream().filter(name -> obj.get(name) == null));
        }

        private Stream<ValidationResult.ValidationError> toErrors(final Stream<String> fields) {
            return fields.map(name -> new ValidationResult.ValidationError(pointer, name + " is required and is not present"));
        }

        @Override
        public String toString() {
            return "Required{" +
                    "required=" + required +
                    ", pointer='" + pointer + '\'' +
                    '}';
        }
    }
}
