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

import io.yupiik.fusion.framework.handlebars.helper.BlockRenderer;
import io.yupiik.fusion.framework.handlebars.helper.HelperContext;
import io.yupiik.fusion.framework.handlebars.helper.SafeString;
import io.yupiik.fusion.framework.handlebars.spi.Accessor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A custom block helper {@code {{#helper args}}: the helper receives the evaluated positional and hash
 * arguments plus block/inverse renderers (the {@code {{else}}} body) and returns the content to output
 * (block output is not escaped, like handlebars.js; a {@link SafeString} result is also rendered raw).
 */
public record BlockHelperPart(Function<HelperContext, Object> helper, List<ArgEvaluator> args,
                              Map<String, ArgEvaluator> hash, Part subPart, Part elsePart,
                              Accessor accessor, List<String> blockParams) implements Part {
    @Override
    public String apply(final RenderContext context, final Object currentData) {
        final var blockRenderer = (BlockRenderer) it -> {
            final var data = it == null ? currentData : it;
            return subPart.apply(context.child(data, accessor), data);
        };
        final var inverseRenderer = elsePart == null ? null : (BlockRenderer) it -> {
            final var data = it == null ? currentData : it;
            return elsePart.apply(context.child(data, accessor), data);
        };
        final var result = helper.apply(new HelperContext(
                Helpers.evalArgs(args, accessor, currentData, context),
                Helpers.evalHash(hash, accessor, currentData, context),
                blockRenderer,
                inverseRenderer));
        if (result == null) {
            return "";
        }
        return result instanceof SafeString s ? s.value() : String.valueOf(result);
    }
}