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

import java.util.Map;
import java.util.function.BiFunction;

public sealed interface Part extends BiFunction<Part.RenderContext, Object, String> permits
        BlockHelperPart, ConstantPart, EachVariablePart, EscapedPart, IfVariablePart, InlineHelperPart,
        NestedVariablePart, ThisHelperPart, UnescapedThisPart, UnescapedVariablePart, UnlessVariablePart,
        EmptyPart, PartListPart {
    /**
     * Per-render context: an immutable stack of frames. Each frame carries the data, the accessor used to
     * resolve `../name` against it and the `as |a b|` block params bound at that level.
     * `../` and `@../index` paths walk up the {@link #parent()} chain at render time.
     */
    record RenderContext(RenderContext parent, Object data, Accessor accessor, Map<String, Object> blockParams) {
        static final RenderContext DEFAULT = new RenderContext(null, null, null, Map.of());

        public RenderContext child(final Object data, final Accessor accessor) {
            return new RenderContext(this, data, accessor, Map.of());
        }

        public RenderContext child(final Object data, final Accessor accessor, final Map<String, Object> blockParams) {
            return new RenderContext(this, data, accessor, blockParams);
        }
    }
}