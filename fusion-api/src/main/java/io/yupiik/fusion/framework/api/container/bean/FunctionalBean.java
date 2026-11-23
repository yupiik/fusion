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
package io.yupiik.fusion.framework.api.container.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/** A shared bean implementation with typed callbacks and its own BaseBean lock/identity. */
public final class FunctionalBean<T> extends BaseBean<T> {
    private final BiFunction<RuntimeContainer, List<Instance<?>>, T> constructor;
    private final Injection<T> injection;
    private final BiConsumer<RuntimeContainer, T> initialization;
    private final BiConsumer<RuntimeContainer, T> destruction;
    private final boolean autoClose;

    public FunctionalBean(final Type type, final Class<?> scope,
                          final int priority, final int destroyPriority, final Map<String, Object> metadata,
                          final BiFunction<RuntimeContainer, List<Instance<?>>, T> constructor,
                          final Injection<T> injection,
                          final BiConsumer<RuntimeContainer, T> initialization,
                          final BiConsumer<RuntimeContainer, T> destruction, final boolean autoClose) {
        super(type, scope, priority, destroyPriority, metadata);
        this.constructor = constructor;
        this.injection = injection;
        this.initialization = initialization;
        this.destruction = destruction;
        this.autoClose = autoClose;
    }

    @Override
    public T create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        final var instance = constructor.apply(container, dependents);
        inject(container, dependents, instance);
        if (initialization != null) {
            initialization.accept(container, instance);
        }
        return instance;
    }

    @Override
    public void inject(final RuntimeContainer container, final List<Instance<?>> dependents, final T instance) {
        if (injection != null) {
            injection.accept(container, dependents, instance);
        }
    }

    @Override
    public void destroy(final RuntimeContainer container, final T instance) {
        if (destruction != null) {
            destruction.accept(container, instance);
        } else if (autoClose) {
            try {
                ((AutoCloseable) instance).close();
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @FunctionalInterface
    public interface Injection<T> {
        void accept(RuntimeContainer container, List<Instance<?>> dependents, T instance);
    }
}
