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

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class MapAccessor implements Accessor {
    public Object find(final Object data, final String name) {
        if (data instanceof List<?> list) {
            return handleList(list, name);
        }

        return handleMap(data, name);
    }

    private Object handleList(final List<?> list, final String name) {
        final var sep = name.indexOf('.');
        if (isDottedAccessor(name, sep)) {
            return find(find(list, name.substring(0, sep)), name.substring(sep + 1));
        }

        try {
            final var idx = Integer.parseInt(name);
            return list.size() > idx ? list.get(idx) : null;
        } catch (final NumberFormatException nfe) {
            return null;
        }
    }

    private Object handleMap(Object data, String name) {
        if (!(data instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Unsupported type '" + data + "'");
        }
        var value = map.get(name); // optimistic case, avoid to browse the string is useless
        if (value == null) {
            final var sep = name.indexOf('.');
            if (isDottedAccessor(name, sep)) {
                return find(find(data, name.substring(0, sep)), name.substring(sep + 1));
            }
        }

        if (value instanceof Supplier<?> s) {
            return s.get();
        }
        return value;
    }

    private boolean isDottedAccessor(final String name, final int nextDot) {
        return nextDot > 0 && nextDot < name.length() - 1;
    }
}
