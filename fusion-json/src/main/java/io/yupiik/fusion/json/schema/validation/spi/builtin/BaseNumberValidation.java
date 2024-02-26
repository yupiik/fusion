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

import java.util.function.Function;
import java.util.stream.Stream;

abstract class BaseNumberValidation extends BaseValidation {
    protected final double bound;

    BaseNumberValidation(final String pointer, final Function<Object, Object> extractor, final double bound) {
        super(pointer, extractor, TypeFilter.NUMBER);
        this.bound = bound;
    }

    @Override
    protected Stream<ValidationResult.ValidationError> onNumber(final Number number) {
        final double val = number.doubleValue();
        if (isValid(val)) {
            return Stream.empty();
        }
        return toError(val);
    }

    protected abstract boolean isValid(double val);

    protected abstract Stream<ValidationResult.ValidationError> toError(double val);
}
