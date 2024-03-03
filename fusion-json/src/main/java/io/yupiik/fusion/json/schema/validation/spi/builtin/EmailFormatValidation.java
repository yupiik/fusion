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

import java.time.OffsetTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class EmailFormatValidation implements ValidationExtension {

    private final Function<String, Predicate<CharSequence>> predicateFactory;

    // from OWASP: https://owasp.org/www-community/OWASP_Validation_Regex_Repository
    private final static String pattern = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}$";

    public EmailFormatValidation(Function<String, Predicate<CharSequence>> predicateFactory) {
        this.predicateFactory = predicateFactory;
    }

    @Override
    public Optional<Function<Object, Stream<ValidationResult.ValidationError>>> create(final ValidationContext model) {
        if ("string".equals(model.schema().get("type")) && "email".equals(model.schema().get("format"))) {
            return Optional.of(new EmailFormatValidation.Impl(model.toPointer(), model.valueProvider(), predicateFactory.apply(pattern)));
        }
        return Optional.empty();
    }

    static class Impl extends BaseValidation {

        private final Predicate<CharSequence> matcher;

        public Impl(final String pointer, final Function<Object, Object> extractor, final Predicate<CharSequence> matcher) {
            super(pointer, extractor, TypeFilter.STRING);
            this.matcher = matcher;
        }

        @Override
        protected Stream<ValidationResult.ValidationError> onString(final String value) {
            if (!matcher.test(value)) {
                return Stream.of(new ValidationResult.ValidationError(pointer, value + " is not an Email format"));
            }
            return Stream.empty();
        }

        @Override
        public String toString() {
            return "EmailFormat{" +
                    "pointer='" + pointer + '\'' +
                    '}';
        }
    }
}
