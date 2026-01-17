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

import io.yupiik.fusion.framework.handlebars.helper.BlockHelperContext;
import io.yupiik.fusion.framework.handlebars.spi.Accessor;

import java.util.List;
import java.util.function.Function;

public record BlockHelperPart(Function<Object, String> helper,
                              List<ArgEvaluator> args,
                              Part subPart,
                              Accessor accessor) implements Part {
    @Deprecated // for backward compatibility only
    public BlockHelperPart(final Function<Object, String> helper, final String name, final Part subPart, final Accessor accessor) {
        this(helper, List.of(new Helpers.DynamicArgEvaluator(name)), subPart, accessor);
    }

    @Override
    public String apply(final RenderContext context, final Object currentData) {
        if (args.size() == 1) {
            final var value = args.get(0).eval(accessor, currentData);
            if (value == null) {
                return "";
            }
            return helper.apply(new BlockHelperContext(value, it -> subPart.apply(context, it)));
        }
        return helper.apply(new BlockHelperContext(
                List.of(args.stream().map(it -> it.eval(accessor, currentData)).toList()),
                it -> subPart.apply(context, it)));
    }
}
