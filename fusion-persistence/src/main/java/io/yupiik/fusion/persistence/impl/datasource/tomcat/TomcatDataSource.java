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
package io.yupiik.fusion.persistence.impl.datasource.tomcat;

import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.persistence.api.SQLFunction;
import io.yupiik.fusion.persistence.api.TransactionManager;
import io.yupiik.fusion.persistence.impl.datasource.SimpleTransactionManager;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PoolProperties;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

import static io.yupiik.fusion.persistence.impl.datasource.tomcat.PoolProperties.toProperties;

public abstract class TomcatDataSource extends DataSource implements AutoCloseable, TransactionManager {
    protected final SimpleTransactionManager txMgr;

    public TomcatDataSource(final Configuration configuration) {
        this(toProperties(configuration));
    }

    public TomcatDataSource(final TomcatDatabaseConfiguration configuration) {
        this(toProperties(configuration));
    }

    public TomcatDataSource(final PoolConfiguration properties) {
        super(properties);
        this.txMgr = new SimpleTransactionManager((context, task) -> withConnection(task));
    }

    // for proxy when produced by CDI
    protected TomcatDataSource() {
        super(new PoolProperties());
        this.txMgr = null;
    }

    /**
     * Binds a connection to current thread in write mode, the result will be committed if there is no error.
     *
     * @param function the task to execute.
     * @param <T>      the returned type.
     * @return the result of the function computation.
     */
    @Override
    public <T> T write(final Function<Connection, T> function) {
        return txMgr.write(function);
    }

    @Override
    public <T> T writeSQL(final SQLFunction<Connection, T> function) {
        return write(c -> {
            try {
                return function.apply(c);
            } catch (final SQLException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /**
     * Binds a connection to current thread in read-only mode, the result will be rolled-backed if needed.
     *
     * @param function the task to execute.
     * @param <T>      the returned type.
     * @return the result of the function computation.
     */
    @Override
    public <T> T read(final Function<Connection, T> function) {
        return txMgr.read(function);
    }

    @Override
    public <T> T readSQL(final SQLFunction<Connection, T> function) {
        return read(c -> {
            try {
                return function.apply(c);
            } catch (final SQLException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    public abstract Connection current();

    public abstract <T> T withConnection(final SQLFunction<Connection, T> function);
}
