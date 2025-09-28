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

import java.util.function.Function;

public final class BlockHelperPart implements Part {

    private final Function<Object, String> helper;

    private final String name;

    private final Part subPart;

    private final Accessor accessor;

    public BlockHelperPart(Function<Object, String> helper, String name, Part subPart, Accessor accessor) {
        this.helper = helper;
        this.name = name;
        this.subPart = subPart;
        this.accessor = accessor;
    }

    @Override
    public String apply(final Part.RenderContext context, final Object currentData) {
        final var value = ".".equals(name) || "this".equals(name) ? currentData : accessor.find(currentData, name);
        if (value == null) {
            return "";
        }
        return helper.apply(new BlockHelperContext(value, it -> subPart.apply(context, it)));
    }

    public Function<Object, String> helper() {
        return helper;
    }

    public String name() {
        return name;
    }

    public Part subPart() {
        return subPart;
    }

    public Accessor accessor() {
        return accessor;
    }
}
