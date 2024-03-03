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

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class PatternValidation implements ValidationExtension {
    private final Function<String, Predicate<CharSequence>> predicateFactory;

    public PatternValidation(final Function<String, Predicate<CharSequence>> predicateFactory) {
        this.predicateFactory = predicateFactory;
    }

    @Override
    public Optional<Function<Object, Stream<ValidationResult.ValidationError>>> create(final ValidationContext model) {
        if (model.schema().getOrDefault("type", "object").equals("string")) {
            return Optional.ofNullable(model.schema().get("pattern"))
                    .filter(TypeFilter.STRING)
                    .map(pattern -> new Impl(model.toPointer(), model.valueProvider(), predicateFactory.apply(pattern.toString())));
        }
        return Optional.empty();
    }

    private static class Impl extends BaseValidation {
        private final Predicate<CharSequence> matcher;

        private Impl(final String pointer, final Function<Object, Object> valueProvider,
                     final Predicate<CharSequence> matcher) {
            super(pointer, valueProvider, TypeFilter.STRING);
            this.matcher = matcher;
        }

        @Override
        public Stream<ValidationResult.ValidationError> onString(final String value) {
            if (!matcher.test(value)) {
                return Stream.of(new ValidationResult.ValidationError(pointer, value + " doesn't match " + matcher));
            }
            return Stream.empty();
        }

        @Override
        public String toString() {
            return "Pattern{" +
                    "regex=" + matcher +
                    ", pointer='" + pointer + '\'' +
                    '}';
        }
    }
}
