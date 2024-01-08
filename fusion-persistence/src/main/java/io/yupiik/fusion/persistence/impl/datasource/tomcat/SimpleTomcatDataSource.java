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
package io.yupiik.fusion.persistence.impl.datasource.tomcat;

import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.persistence.api.SQLFunction;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;

import java.sql.Connection;
import java.sql.SQLException;

public class SimpleTomcatDataSource extends TomcatDataSource {
    public SimpleTomcatDataSource(final Configuration configuration) {
        super(configuration);
    }

    public SimpleTomcatDataSource(final TomcatDatabaseConfiguration configuration) {
        super(configuration);
    }

    public SimpleTomcatDataSource(final PoolConfiguration properties) {
        super(properties);
    }

    protected SimpleTomcatDataSource() {
        super();
    }

    @Override
    public Connection current() {
        try {
            return getConnection();
        } catch (final SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public <T> T withConnection(final SQLFunction<Connection, T> function) {
        Connection conRef = null;
        try (final var connection = super.getConnection()) {
            conRef = connection;
            return txMgr.executeWithoutAutoCommit(connection, function);
        } catch (final SQLException ex) {
            try {
                if (conRef != null && !conRef.isClosed()  && !conRef.getAutoCommit()) {
                    conRef.rollback();
                }
            } catch (final SQLException e) {
                // no-op
            }
            throw new IllegalStateException(ex);
        }
    }
}
