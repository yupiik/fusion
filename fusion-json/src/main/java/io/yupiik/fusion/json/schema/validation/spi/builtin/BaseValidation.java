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

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

public abstract class BaseValidation implements Function<Object, Stream<ValidationResult.ValidationError>> {
    protected final String pointer;
    protected final Function<Object, Object> extractor;
    private final TypeFilter typeValidator;
    private final boolean rootCanBeNull;

    public BaseValidation(final String pointer, final Function<Object, Object> extractor, final TypeFilter typeValidator) {
        this.pointer = pointer;
        this.extractor = extractor != null ? extractor : v -> v;
        this.rootCanBeNull = extractor != null;
        this.typeValidator = typeValidator;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Stream<ValidationResult.ValidationError> apply(final Object obj) {
        if (obj == null && rootCanBeNull) {
            return Stream.empty();
        }

        final var value = extractor.apply(obj);
        if (!typeValidator.test(value)) {
            return Stream.empty();
        }

        if (value instanceof String s) {
            return onString(s);
        }
        if (value instanceof Number n) {
            return onNumber(n);
        }
        if (value instanceof Boolean b) {
            return onBoolean(b);
        }
        if (value instanceof Collection<?> c) {
            return onArray(c);
        }
        if (value instanceof Map<?, ?> m) {
            return onObject((Map<String, Object>) m);
        }
        if (value == null) {
            return Stream.empty();
        }
        throw new IllegalArgumentException("Unsupported value type: " + value);
    }

    protected Stream<ValidationResult.ValidationError> onArray(final Collection<?> array) {
        return Stream.empty();
    }

    protected Stream<ValidationResult.ValidationError> onObject(final Map<String, Object> object) {
        return Stream.empty();
    }

    protected Stream<ValidationResult.ValidationError> onNumber(final Number number) {
        return Stream.empty();
    }

    protected Stream<ValidationResult.ValidationError> onBoolean(final boolean value) {
        return Stream.empty();
    }

    protected Stream<ValidationResult.ValidationError> onString(final String string) {
        return Stream.empty();
    }
}
