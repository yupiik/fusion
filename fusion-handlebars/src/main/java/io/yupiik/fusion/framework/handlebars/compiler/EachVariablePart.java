/*
 * Copyright (c) 2022-2023 - Yupiik SAS - https://www.yupiik.com
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
package io.yupiik.fusion.framework.handlebars.compiler;

import java.util.Collection;
import java.util.Map;

import static java.util.stream.Collectors.joining;

public record EachVariablePart(String name, Part item) implements Part {
    @Override
    public String apply(final RenderContext context, final Object currentData) {
        if (!(currentData instanceof Map<?,?> map)) {
            throw new IllegalArgumentException("Unsupported type '" + currentData + "'");
        }
        final var value = map.get(name);
        if (value == null) {
            return "";
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(it -> item.apply(context, it)).collect(joining("\n"));
        }
        if (value instanceof Map<? ,?> nestedMap) {
            return nestedMap.entrySet().stream().map(it -> item.apply(context, it)).collect(joining("\n"));
        }
        throw new IllegalArgumentException("Unsupported each for " + value);
    }
}
