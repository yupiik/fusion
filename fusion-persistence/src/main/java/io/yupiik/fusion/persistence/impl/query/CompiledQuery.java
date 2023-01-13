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
package io.yupiik.fusion.persistence.impl.query;

import io.yupiik.fusion.persistence.impl.DatabaseImpl;

import java.sql.Connection;
import java.sql.SQLException;

public class CompiledQuery<T> {
    private final DatabaseImpl database;
    private final QueryKey<T> key;
    private volatile String[] columnNames;

    public CompiledQuery(final DatabaseImpl database, final QueryKey<T> queryKey) {
        this.database = database;
        this.key = queryKey;
    }

    public String[] getColumnNames() {
        return columnNames;
    }

    public CompiledQuery<T> setColumnNames(final String[] columnNames) {
        this.columnNames = columnNames;
        return this;
    }

    public QueryKey<T> getKey() {
        return key;
    }

    public DatabaseImpl getDatabase() {
        return database;
    }

    public StatementBinderImpl apply(final Connection connection) throws SQLException {
        return new StatementBinderImpl(database, key.getSql(), connection);
    }
}
