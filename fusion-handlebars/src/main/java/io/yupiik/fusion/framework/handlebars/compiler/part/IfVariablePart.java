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
package io.yupiik.fusion.framework.handlebars.compiler.part;

import io.yupiik.fusion.framework.handlebars.helper.Truthiness;
import io.yupiik.fusion.framework.handlebars.spi.Accessor;

/**
 * {@code {{#if condition}}} renders the block when the condition is truthy, the {@code {{else}}} branch otherwise.
 */
public record IfVariablePart(ArgEvaluator condition, Part next, Part elsePart, Accessor accessor) implements Part {
    @Override
    public String apply(final RenderContext context, final Object currentData) {
        if (Truthiness.INSTANCE.isFalsy(condition.eval(accessor, currentData, context))) {
            return elsePart == null ? "" : elsePart.apply(context, currentData);
        }
        return next.apply(context, currentData);
    }
}