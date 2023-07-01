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
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.persistence.api.TransactionManager;
import io.yupiik.fusion.persistence.impl.datasource.SimpleTransactionManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class FusionTransactionManagerBean extends BaseBean<TransactionManager> {
    public FusionTransactionManagerBean() {
        super(TransactionManager.class, ApplicationScoped.class, 0, Map.of());
    }

    @Override
    public TransactionManager create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        final var dataSource = lookup(container, DataSource.class, dependents);
        return new SimpleTransactionManager(task -> {
            Connection conRef = null;
            try (final var connection = dataSource.getConnection()) {
                conRef = connection;
                return task.apply(connection);
            } catch (final SQLException ex) {
                try {
                    if (conRef != null && !conRef.isClosed()) {
                        conRef.rollback();
                    }
                } catch (final SQLException e) {
                    // no-op
                }
                throw new IllegalStateException(ex);
            }
        });
    }
}
