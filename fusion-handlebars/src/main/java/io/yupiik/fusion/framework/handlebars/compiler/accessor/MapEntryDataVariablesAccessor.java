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
package io.yupiik.fusion.framework.handlebars.compiler.accessor;

import io.yupiik.fusion.framework.handlebars.spi.Accessor;

import java.util.Map;

public class MapEntryDataVariablesAccessor implements Accessor {
    private final Accessor delegate;

    public MapEntryDataVariablesAccessor(final Accessor delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object find(final Object data, final String name) {
        return switch (name) {
            case "@key" -> data instanceof Map.Entry<?, ?> e ? e.getKey() : null;
            case "@value" -> data instanceof Map.Entry<?, ?> e ? e.getValue() : null;
            default -> delegate.find(data, name);
        };
    }
}
