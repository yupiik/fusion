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

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class MinimumValidation implements ValidationExtension {
    @Override
    public Optional<Function<Object, Stream<ValidationResult.ValidationError>>> create(final ValidationContext model) {
        if (model.schema().getOrDefault("type", "object").equals("number")) {
            return Optional.ofNullable(model.schema().get("minimum"))
                    .filter(Number.class::isInstance)
                    .map(Number.class::cast)
                    .map(m -> new Impl(model.toPointer(), model.valueProvider(), m.doubleValue()));
        }
        return Optional.empty();
    }

    private static class Impl extends BaseNumberValidation {
        private Impl(final String pointer, final Function<Object, Object> valueProvider, final double bound) {
            super(pointer, valueProvider, bound);
        }

        @Override
        protected boolean isValid(final double val) {
            return val >= this.bound;
        }

        @Override
        protected Stream<ValidationResult.ValidationError> toError(final double val) {
            return Stream.of(new ValidationResult.ValidationError(pointer, val + " is less than " + this.bound));
        }

        @Override
        public String toString() {
            return "Minimum{factor=" + bound + ", pointer='" + pointer + "'}";
        }
    }
}
