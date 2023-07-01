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
package io.yupiik.fusion.persistence.impl;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

public class ThrowingDataSource implements DataSource {
    @Override
    public Connection getConnection() throws SQLException {
        return fail();
    }

    @Override
    public Connection getConnection(final String username, final String password) throws SQLException {
        return fail();
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return fail();
    }

    @Override
    public void setLogWriter(final PrintWriter out) throws SQLException {
        fail();
    }

    @Override
    public void setLoginTimeout(final int seconds) throws SQLException {
        fail();
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return fail();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return fail();
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        return fail();
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return fail();
    }

    private static <T> T fail() {
        throw new UnsupportedOperationException("Ensure to only call methods with explicit Connection usage");
    }
}
