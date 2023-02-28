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
package io.yupiik.fusion.persistence.impl.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.persistence.api.Database;
import io.yupiik.fusion.persistence.api.Entity;
import io.yupiik.fusion.persistence.impl.DatabaseConfiguration;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.stream.Collectors.toMap;

public class FusionDatabaseBean extends BaseBean<Database> {
    private final AtomicBoolean closed = new AtomicBoolean();

    public FusionDatabaseBean() {
        super(Database.class, ApplicationScoped.class, 1000, Map.of());
    }

    @Override
    public void destroy(final RuntimeContainer container, final Database instance) {
        closed.set(true);
        super.destroy(container, instance);
    }

    @Override
    public Database create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        final var configuration = lookup(container, DatabaseConfiguration.class, dependents);

        if (configuration.getInstanceLookup() == null) {
            final var entitiesInstance = new LazyEntitiesInstance(container, dependents, closed);
            dependents.add(entitiesInstance);
            configuration.setInstanceLookup(k -> entitiesInstance.instance().get(k));
        }

        return Database.of(configuration);
    }

    private static class LazyEntitiesInstance implements Instance<Map<Type, Object>> {
        private final RuntimeContainer container;
        private final List<Instance<?>> dependents;
        private final AtomicBoolean closed;
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
            synchronized (this) {
                if (entities != null) {
                    return entities.instance();
                }
                if (closed.get()) {
                    return Map.of();
                }
                synchronized (this) {
                    if (entities == null) {
                        entities = container.lookups(
                                Entity.class,
                                i -> i.stream().collect(toMap(
                                        it -> it.instance().getRootType(),
                                        Instance::instance)));
                        dependents.add(entities);
                    }
                }
                return entities.instance();
            }
        }
    }
}
