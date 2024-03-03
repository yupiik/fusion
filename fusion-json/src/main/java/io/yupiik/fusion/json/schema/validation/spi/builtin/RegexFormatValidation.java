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

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public class RegexFormatValidation implements ValidationExtension {

    @Override
    public Optional<Function<Object, Stream<ValidationResult.ValidationError>>> create(final ValidationContext model) {
        if ("string".equals(model.schema().get("type")) && "regex".equals(model.schema().get("format"))) {
            return Optional.of(new RegexFormatValidation.Impl(model.toPointer(), model.valueProvider()));
        }
        return Optional.empty();
    }

    static class Impl extends BaseValidation {

        public Impl(final String pointer, final Function<Object, Object> extractor) {
            super(pointer, extractor, TypeFilter.STRING);
        }

        @Override
        protected Stream<ValidationResult.ValidationError> onString(final String value) {
            try {
                Pattern.compile(value);
                return Stream.empty();
            } catch (PatternSyntaxException exception) {
                return Stream.of(new ValidationResult.ValidationError(pointer, value + " is not a Regex format"));
            }
        }

        @Override
        public String toString() {
            return "RegexFormat{" +
                    "pointer='" + pointer + '\'' +
                    '}';
        }
    }
}
