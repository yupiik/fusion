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

import io.yupiik.fusion.framework.handlebars.spi.Accessor;

public final class UnescapedVariablePart implements Part {

    private final String name;

    private final Accessor accessor;

    public UnescapedVariablePart(String name, Accessor accessor) {
        this.name = name;
        this.accessor = accessor;
    }

    public String name() {
        return name;
    }

    public Accessor accessor() {
        return accessor;
    }

    @Override
    public String apply(final RenderContext context, final Object currentData) {
        final var value = accessor.find(currentData, name);
        return value == null ? "" : String.valueOf(value);
    }
}
