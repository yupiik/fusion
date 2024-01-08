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
package io.yupiik.fusion.framework.handlebars.compiler.accessor;

import io.yupiik.fusion.framework.handlebars.spi.Accessor;

import java.util.Map;

/**
 * Uses the data type to find the related accessor.
 * Enables to use the default accessor when type is unknown and a specific one (for a record for ex) when the type is known.
 * It uses {@code data.getClass()} to "route" to the right accessor and falls back on {@code defaultAccessor} which is the map one if not set.
 *
 * @param defaultAccessor the accessor to use when no specific accessor is set in delegates.
 * @param delegates       the type specific accessors.
 */
public record RoutingAccessor(Accessor defaultAccessor, Map<Class<?>, Accessor> delegates) implements Accessor {
    public RoutingAccessor(final Map<Class<?>, Accessor> delegates) {
        this(new MapAccessor(), delegates);
    }

    @Override
    public Object find(final Object data, final String name) {
        if (data == null) {
            return null;
        }
        final var specific = delegates.get(data.getClass());
        return specific == null ? defaultAccessor : specific;
    }
}
