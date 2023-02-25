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

import io.yupiik.fusion.persistence.api.SQLFunction;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PoolProperties;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.ShardingKey;
import java.sql.Statement;
import java.sql.Struct;
import java.sql.Wrapper;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.function.Function;

public class TomcatDataSource extends DataSource implements AutoCloseable {
    private final ThreadLocal<Connection> connectionThreadLocal = new ThreadLocal<>();

    public TomcatDataSource(final TomcatDatabaseConfiguration configuration) {
        super(toProperties(configuration));
    }

    public TomcatDataSource(final PoolProperties properties) {
        super(properties);
    }

    // for proxy when produced by CDI
    protected TomcatDataSource() {
        super(new PoolProperties());
    }

    /**
     * Binds a connection to current thread in write mode, the result will be committed if there is no error.
     *
     * @param function the task to execute.
     * @param <T>      the returned type.
     * @return the result of the function computation.
     */
    public <T> T write(final Function<Connection, T> function) {
        return withConnection(connection -> {
            final var result = function.apply(connection);
            connection.commit();
            return result;
        });
    }

    /**
     * Binds a connection to current thread in read-only mode, the result will be rolle-backed if needed.
     *
     * @param function the task to execute.
     * @param <T>      the returned type.
     * @return the result of the function computation.
     */
    public <T> T read(final Function<Connection, T> function) {
        return withConnection(connection -> {
            final var readOnly = connection.isReadOnly();
            connection.setReadOnly(true);
            try {
                return function.apply(connection);
            } finally {
                if (!readOnly) {
                    connection.setReadOnly(false);
                }
                if (!connection.isClosed()) {
                    connection.rollback();
                }
            }
        });
    }

    @Override
    public Connection getConnection() {
        final var existing = current();
        if (existing == null) {
            throw new IllegalStateException("No contextual connection, ensure to use DataSourceTx around your code");
        }
        return existing;
    }

    public Connection current() {
        final var connection = connectionThreadLocal.get();
        if (connection == null) {
            connectionThreadLocal.remove();
        }
        return connection;
    }

    public <T> T withConnection(final SQLFunction<Connection, T> function) {
        try (final var connection = super.getConnection()) {
            connectionThreadLocal.set(wrap(connection));
            final var original = disableAutoCommit(connection);
            try {
                return function.apply(connection);
            } catch (final RuntimeException | Error re) {
                if (!connection.isClosed()) {
                    connection.rollback();
                }
                throw re;
            } finally {
                restoreAutoCommit(connection, original);
            }
        } catch (final SQLException ex) {
            throw new IllegalStateException(ex);
        } finally {
            connectionThreadLocal.remove();
        }
    }

    private Connection wrap(final Connection connection) {
        return new ThreadLocalConnection(connection);
    }

    private boolean disableAutoCommit(final Connection connection) throws SQLException {
        final var original = connection.getAutoCommit();
        if (original) {
            connection.setAutoCommit(false);
        }
        return original;
    }

    private void restoreAutoCommit(final Connection connection, final boolean original) throws SQLException {
        if (original) {
            connection.setAutoCommit(true);
        }
    }

    private static PoolConfiguration toProperties(final TomcatDatabaseConfiguration db) {
        final var properties = new PoolProperties();
        properties.setDriverClassName(db.driver());
        properties.setUrl(db.url());
        properties.setUsername(db.username());
        properties.setPassword(db.password());
        properties.setTestOnBorrow(db.testOnBorrow());
        properties.setTestOnReturn(db.testOnReturn());
        properties.setTestWhileIdle(db.testWhileIdle());
        properties.setMinEvictableIdleTimeMillis(db.minEvictableIdleTime());
        properties.setTimeBetweenEvictionRunsMillis(db.timeBetweenEvictionRuns());
        properties.setValidationQuery(db.validationQuery());
        properties.setValidationQueryTimeout(db.validationQueryTimeout());
        properties.setDefaultAutoCommit(db.defaultAutoCommit());
        properties.setMinIdle(db.minIdle());
        properties.setMaxActive(db.maxActive());
        properties.setMaxIdle(db.maxActive());
        properties.setRemoveAbandoned(db.removeAbandoned());
        properties.setRemoveAbandonedTimeout(db.removeAbandonedTimeout());
        properties.setLogAbandoned(db.logAbandoned());
        return properties;
    }

    private record ThreadLocalConnection(Connection connection) implements Connection, Wrapper {
        @Override
        public void close() {
            // skipped
            // connection.close();
        }

        @Override
        public Statement createStatement() throws SQLException {
            return connection.createStatement();
        }

        @Override
        public PreparedStatement prepareStatement(final String sql) throws SQLException {
            return connection.prepareStatement(sql);
        }

        @Override
        public CallableStatement prepareCall(final String sql) throws SQLException {
            return connection.prepareCall(sql);
        }

        @Override
        public String nativeSQL(final String sql) throws SQLException {
            return connection.nativeSQL(sql);
        }

        @Override
        public void setAutoCommit(final boolean autoCommit) throws SQLException {
            connection.setAutoCommit(autoCommit);
        }

        @Override
        public boolean getAutoCommit() throws SQLException {
            return connection.getAutoCommit();
        }

        @Override
        public void commit() throws SQLException {
            connection.commit();
        }

        @Override
        public void rollback() throws SQLException {
            connection.rollback();
        }

        @Override
        public boolean isClosed() throws SQLException {
            return connection.isClosed();
        }

        @Override
        public DatabaseMetaData getMetaData() throws SQLException {
            return connection.getMetaData();
        }

        @Override
        public void setReadOnly(final boolean readOnly) throws SQLException {
            connection.setReadOnly(readOnly);
        }

        @Override
        public boolean isReadOnly() throws SQLException {
            return connection.isReadOnly();
        }

        @Override
        public void setCatalog(final String catalog) throws SQLException {
            connection.setCatalog(catalog);
        }

        @Override
        public String getCatalog() throws SQLException {
            return connection.getCatalog();
        }

        @Override
        public void setTransactionIsolation(final int level) throws SQLException {
            connection.setTransactionIsolation(level);
        }

        @Override
        public int getTransactionIsolation() throws SQLException {
            return connection.getTransactionIsolation();
        }

        @Override
        public SQLWarning getWarnings() throws SQLException {
            return connection.getWarnings();
        }

        @Override
        public void clearWarnings() throws SQLException {
            connection.clearWarnings();
        }

        @Override
        public Statement createStatement(final int resultSetType, final int resultSetConcurrency) throws SQLException {
            return connection.createStatement(resultSetType, resultSetConcurrency);
        }

        @Override
        public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency) throws SQLException {
            return connection.prepareStatement(sql, resultSetType, resultSetConcurrency);
        }

        @Override
        public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency) throws SQLException {
            return connection.prepareCall(sql, resultSetType, resultSetConcurrency);
        }

        @Override
        public Map<String, Class<?>> getTypeMap() throws SQLException {
            return connection.getTypeMap();
        }

        @Override
        public void setTypeMap(final Map<String, Class<?>> map) throws SQLException {
            connection.setTypeMap(map);
        }

        @Override
        public void setHoldability(final int holdability) throws SQLException {
            connection.setHoldability(holdability);
        }

        @Override
        public int getHoldability() throws SQLException {
            return connection.getHoldability();
        }

        @Override
        public Savepoint setSavepoint() throws SQLException {
            return connection.setSavepoint();
        }

        @Override
        public Savepoint setSavepoint(final String name) throws SQLException {
            return connection.setSavepoint(name);
        }

        @Override
        public void rollback(final Savepoint savepoint) throws SQLException {
            connection.rollback(savepoint);
        }

        @Override
        public void releaseSavepoint(final Savepoint savepoint) throws SQLException {
            connection.releaseSavepoint(savepoint);
        }

        @Override
        public Statement createStatement(final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
            return connection.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
            return connection.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
            return connection.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys) throws SQLException {
            return connection.prepareStatement(sql, autoGeneratedKeys);
        }

        @Override
        public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes) throws SQLException {
            return connection.prepareStatement(sql, columnIndexes);
        }

        @Override
        public PreparedStatement prepareStatement(final String sql, final String[] columnNames) throws SQLException {
            return connection.prepareStatement(sql, columnNames);
        }

        @Override
        public Clob createClob() throws SQLException {
            return connection.createClob();
        }

        @Override
        public Blob createBlob() throws SQLException {
            return connection.createBlob();
        }

        @Override
        public NClob createNClob() throws SQLException {
            return connection.createNClob();
        }

        @Override
        public SQLXML createSQLXML() throws SQLException {
            return connection.createSQLXML();
        }

        @Override
        public boolean isValid(final int timeout) throws SQLException {
            return connection.isValid(timeout);
        }

        @Override
        public void setClientInfo(final String name, final String value) throws SQLClientInfoException {
            connection.setClientInfo(name, value);
        }

        @Override
        public void setClientInfo(final Properties properties) throws SQLClientInfoException {
            connection.setClientInfo(properties);
        }

        @Override
        public String getClientInfo(final String name) throws SQLException {
            return connection.getClientInfo(name);
        }

        @Override
        public Properties getClientInfo() throws SQLException {
            return connection.getClientInfo();
        }

        @Override
        public Array createArrayOf(final String typeName, final Object[] elements) throws SQLException {
            return connection.createArrayOf(typeName, elements);
        }

        @Override
        public Struct createStruct(final String typeName, final Object[] attributes) throws SQLException {
            return connection.createStruct(typeName, attributes);
        }

        @Override
        public void setSchema(final String schema) throws SQLException {
            connection.setSchema(schema);
        }

        @Override
        public String getSchema() throws SQLException {
            return connection.getSchema();
        }

        @Override
        public void abort(final Executor executor) throws SQLException {
            connection.abort(executor);
        }

        @Override
        public void setNetworkTimeout(final Executor executor, final int milliseconds) throws SQLException {
            connection.setNetworkTimeout(executor, milliseconds);
        }

        @Override
        public int getNetworkTimeout() throws SQLException {
            return connection.getNetworkTimeout();
        }

        @Override
        public void beginRequest() throws SQLException {
            connection.beginRequest();
        }

        @Override
        public void endRequest() throws SQLException {
            connection.endRequest();
        }

        @Override
        public boolean setShardingKeyIfValid(final ShardingKey shardingKey, final ShardingKey superShardingKey, final int timeout) throws SQLException {
            return connection.setShardingKeyIfValid(shardingKey, superShardingKey, timeout);
        }

        @Override
        public boolean setShardingKeyIfValid(final ShardingKey shardingKey, final int timeout) throws SQLException {
            return connection.setShardingKeyIfValid(shardingKey, timeout);
        }

        @Override
        public void setShardingKey(final ShardingKey shardingKey, final ShardingKey superShardingKey) throws SQLException {
            connection.setShardingKey(shardingKey, superShardingKey);
        }

        @Override
        public void setShardingKey(final ShardingKey shardingKey) throws SQLException {
            connection.setShardingKey(shardingKey);
        }

        @Override
        public <T> T unwrap(final Class<T> iface) throws SQLException {
            return connection.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(final Class<?> iface) throws SQLException {
            return connection.isWrapperFor(iface);
        }

        @Override
        public boolean equals(final Object obj) {
            return this == obj || obj instanceof Connection c && (Objects.equals(c, connection));
        }
    }
}
