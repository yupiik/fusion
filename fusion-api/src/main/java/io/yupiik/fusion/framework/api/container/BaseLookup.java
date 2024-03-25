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
package io.yupiik.fusion.framework.api.container;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Function;

public abstract class BaseLookup {
    protected <A, T> T lookups(final RuntimeContainer container,
                               final Class<A> type,
                               final Function<List<Instance<A>>, T> postProcessor,
                               final List<Instance<?>> deps) {
        final var i = container.lookups(type, postProcessor);
        deps.add(i);
        return i.instance();
    }

    protected Object lookup(final RuntimeContainer container, final Type type, final List<Instance<?>> deps) {
        final var i = container.lookup(type);
        deps.add(i);
        return i.instance();
    }

    protected <T> T lookup(final RuntimeContainer container, final Class<T> type, final List<Instance<?>> deps) {
        return type.cast(lookup(container, (Type) type, deps));
    }
}
