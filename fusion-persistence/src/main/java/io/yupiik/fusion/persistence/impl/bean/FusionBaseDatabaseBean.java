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
package io.yupiik.fusion.persistence.impl.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.persistence.api.Entity;
import io.yupiik.fusion.persistence.impl.DatabaseConfiguration;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.stream.Collectors.toMap;

public abstract class FusionBaseDatabaseBean<T> extends BaseBean<T> {
    private final AtomicBoolean closed = new AtomicBoolean();

    protected FusionBaseDatabaseBean(final Class<T> type) {
        super(type, ApplicationScoped.class, 1000, Map.of());
    }

    @Override
    public void destroy(final RuntimeContainer container, final T instance) {
        closed.set(true);
        super.destroy(container, instance);
    }

    @Override
    public T create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        final var configuration = lookup(container, DatabaseConfiguration.class, dependents);
        fixConfig(container, dependents, configuration);
        return doCreate(configuration);
    }

    protected abstract T doCreate(final DatabaseConfiguration configuration);

    protected void fixConfig(final RuntimeContainer container, final List<Instance<?>> dependents, final DatabaseConfiguration configuration) {
        if (configuration.getInstanceLookup() == null) {
            final var entitiesInstance = new LazyEntitiesInstance(container, dependents, closed);
            dependents.add(entitiesInstance);
            configuration.setInstanceLookup(k -> entitiesInstance.instance().get(k));
        }
    }

    private static class LazyEntitiesInstance implements Instance<Map<Type, Object>> {
        private final RuntimeContainer container;
        private final List<Instance<?>> dependents;
        private final AtomicBoolean closed;
        private final Lock lock = new ReentrantLock();
        private volatile Instance<Map<Type, Object>> entities;

        private LazyEntitiesInstance(final RuntimeContainer container, final List<Instance<?>> dependents, final AtomicBoolean closed) {
            this.container = container;
            this.dependents = dependents;
            this.closed = closed;
        }

        @Override
        public void close() {
            if (entities != null) {
                entities.close();
            }
        }

        @Override
        public FusionBean<Map<Type, Object>> bean() {
            return null;
        }

        @Override
        public Map<Type, Object> instance() {
            if (entities != null) {
                return entities.instance();
            }
            if (closed.get()) {
                return Map.of();
            }
            lock.lock();
            try {
                if (entities != null) {
                    return entities.instance();
                }
                if (closed.get()) {
                    return Map.of();
                }

                entities = container.lookups(
                        Entity.class,
                        i -> i.stream().collect(toMap(
                                it -> it.instance().getRootType(),
                                Instance::instance)));
                dependents.add(entities);
                return entities.instance();
            } finally {
                lock.unlock();
            }
        }
    }
}
