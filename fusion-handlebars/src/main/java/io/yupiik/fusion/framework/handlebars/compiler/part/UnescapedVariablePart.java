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

import io.yupiik.fusion.framework.handlebars.helper.SafeString;
import io.yupiik.fusion.framework.handlebars.spi.Accessor;

/**
 * A raw variable path {@code {{{name}}}}: renders the resolved value without escaping.
 */
public record UnescapedVariablePart(ArgEvaluator name, Accessor accessor) implements Part {
    @Override
    public String apply(final RenderContext context, final Object currentData) {
        final var value = name.eval(accessor, currentData, context);
        if (value == null) {
            return "";
        }
        return value instanceof SafeString s ? s.value() : String.valueOf(value);
    }
}