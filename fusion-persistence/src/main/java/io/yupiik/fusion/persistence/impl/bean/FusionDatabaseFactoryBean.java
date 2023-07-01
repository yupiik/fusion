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
import io.yupiik.fusion.framework.api.container.DefaultInstance;
import io.yupiik.fusion.framework.api.container.DelegatingRuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.persistence.api.ContextLessDatabase;
import io.yupiik.fusion.persistence.api.Database;
import io.yupiik.fusion.persistence.api.DatabaseFactory;
import io.yupiik.fusion.persistence.impl.ContextLessDatabaseConfiguration;
import io.yupiik.fusion.persistence.impl.DatabaseConfiguration;
import io.yupiik.fusion.persistence.impl.DelegatingContextLessDatabase;
import io.yupiik.fusion.persistence.impl.DelegatingDatabase;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FusionDatabaseFactoryBean extends BaseBean<DatabaseFactory> {
    public FusionDatabaseFactoryBean() {
        super(DatabaseFactory.class, ApplicationScoped.class, 1000, Map.of());
    }

    @Override
    public void destroy(final RuntimeContainer container, final DatabaseFactory instance) {
        super.destroy(container, instance);
    }

    @Override
    public DatabaseFactory create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        return new DatabaseFactoryImpl(container);
    }

    private static class DatabaseFactoryImpl implements DatabaseFactory {
        private final RuntimeContainer container;

        private DatabaseFactoryImpl(final RuntimeContainer container) {
            this.container = container;
        }

        @Override
        public CloseableDatabase create(final DatabaseConfiguration configuration) {
            final var databaseBean = findDatabaseBean();
            final var dependents = new ArrayList<Instance<?>>();
            final var containerOverride = getContainerOverride(configuration);
            final var database = databaseBean.create(containerOverride, dependents);
            return new CloseableDatabaseImpl(database, new DefaultInstance<>(databaseBean, containerOverride, database, dependents));
        }

        @Override
        public CloseableContextLessDatabase create(final ContextLessDatabaseConfiguration<?> configuration) {
            final var databaseBean = findContextLessDatabaseBean();
            final var dependents = new ArrayList<Instance<?>>();
            final var containerOverride = getContainerOverride(configuration);
            final var database = databaseBean.create(containerOverride, dependents);
            return new CloseableContextLessDatabaseImpl(
                    database, new DefaultInstance<>(databaseBean, containerOverride, database, dependents));
        }

        @SuppressWarnings("unchecked")
        private FusionBean<Database> findDatabaseBean() {
            return (FusionBean<Database>) container.getBeans().getBeans().get(Database.class);
        }

        @SuppressWarnings("unchecked")
        private FusionBean<ContextLessDatabase> findContextLessDatabaseBean() {
            return (FusionBean<ContextLessDatabase>) container.getBeans().getBeans().get(ContextLessDatabase.class);
        }

        private DelegatingRuntimeContainer getContainerOverride(final ContextLessDatabaseConfiguration configuration) {
            return new DelegatingRuntimeContainer(container) { // forward the right instance for entities
                private final Instance<ContextLessDatabaseConfiguration<?>> configurationInstance = new DefaultInstance<ContextLessDatabaseConfiguration<?>>(
                        null, this, configuration, List.of());

                @Override
                @SuppressWarnings("unchecked")
                public <T> Instance<T> lookup(final Type type) {
                    return type == DatabaseConfiguration.class || ContextLessDatabaseConfiguration.class == type ?
                            (Instance<T>) configurationInstance :
                            super.lookup(type);
                }
            };
        }
    }

    private static class CloseableDatabaseImpl extends DelegatingDatabase implements DatabaseFactory.CloseableDatabase {
        private final AutoCloseable close;

        private CloseableDatabaseImpl(final Database delegate, final AutoCloseable close) {
            super(delegate);
            this.close = close;
        }

        @Override
        public void close() throws Exception {
            close.close();
        }
    }

    private static class CloseableContextLessDatabaseImpl extends DelegatingContextLessDatabase implements DatabaseFactory.CloseableContextLessDatabase {
        private final AutoCloseable close;

        private CloseableContextLessDatabaseImpl(final ContextLessDatabase delegate, final AutoCloseable close) {
            super(delegate);
            this.close = close;
        }

        @Override
        public void close() throws Exception {
            close.close();
        }
    }
}
