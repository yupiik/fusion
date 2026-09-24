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

import java.util.List;
import java.util.Map;

/**
 * {@code {{#with value}}} renders the block with {@code value} as the current data when it is truthy,
 * the {@code {{else}}} branch otherwise. With {@code {{#with value as |foo|}}}, the first block param
 * is bound to {@code value} and {@code ../} inside the block refers to the enclosing data.
 */
public record NestedVariablePart(ArgEvaluator value, Part next, Part elsePart, Accessor accessor,
                                 List<String> blockParams) implements Part {
    @Override
    public String apply(final RenderContext context, final Object currentData) {
        final var resolved = value.eval(accessor, currentData, context);
        if (Truthiness.INSTANCE.isFalsy(resolved)) {
            return elsePart == null ? "" : elsePart.apply(context, currentData);
        }
        return next.apply(context.child(resolved, accessor, bind(resolved)), resolved);
    }

    private Map<String, Object> bind(final Object resolved) {
        return blockParams.isEmpty() ? Map.of() : Map.of(blockParams.get(0), resolved);
    }
}