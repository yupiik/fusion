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

public class PrecomputedChainAccessor implements Accessor {
    private final String key;
    private final String supportedName;
    private final Accessor delegating;
    private final Accessor next;

    public PrecomputedChainAccessor(final String key, final String supportedName, final Accessor delegating, final Accessor next) {
        this.key = key;
        this.supportedName = supportedName;
        this.delegating = delegating;
        this.next = next;
    }

    public String getSupportedName() {
        return supportedName;
    }

    @Override
    public Object find(final Object data, final String name) {
        final var init = !supportedName.equals(name) ? null : delegating.find(data, key);
        if (init == null) {
            return null;
        }
        return next.find(init, name.substring(key.length() + 1));
    }
}
