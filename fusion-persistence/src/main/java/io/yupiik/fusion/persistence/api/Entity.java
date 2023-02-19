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
package io.yupiik.fusion.persistence.api;

import java.lang.reflect.Type;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public interface Entity<E, ID> {
    Class<?> getRootType();

    String getTable();

    String getFindByIdQuery();

    String getUpdateQuery();

    String getDeleteQuery();

    String getInsertQuery();

    String getCountAllQuery();

    String getFindAllQuery();

    List<ColumnMetadata> getOrderedColumns();

    boolean isAutoIncremented();

    E onInsert(final E instance, final PreparedStatement statement) throws SQLException;

    void onDelete(final E instance, final PreparedStatement statement) throws SQLException;

    E onUpdate(final E instance, final PreparedStatement statement) throws SQLException;

    void onFindById(final ID instance, final PreparedStatement stmt) throws SQLException;

    E onAfterInsert(final E instance, final PreparedStatement statement) throws SQLException;

    /**
     * Creates a string usable when building a SQL query.
     * It is typically useful for JOIN queries.
     * Usage:
     * {@code String selectedFieldsForEntityE = entityE.concatenateColumns(new ColumnsConcatenationRequest().setPrefix("tableAlias.").setIgnored(Set.of("fk")));}
     *
     * @param request how to concatenate the fields.
     * @return the string as described before.
     */
    String concatenateColumns(ColumnsConcatenationRequest request);

    /**
     * {@see #mapFromPrefix(String, String...)}.
     *
     * @param prefix    prefix to add to the column names for the mapping (often used in combination with {@link #concatenateColumns(ColumnsConcatenationRequest)}).
     * @param resultSet resultSet to check column names from.
     * @return the entity mapped (note that with a left join you can get an instance with only null fields).
     */
    Function<ResultSet, E> mapFromPrefix(String prefix, ResultSet resultSet);

    /**
     * Same as {@link #mapFromPrefix(String, ResultSet)} but from a precomputed column names set.
     * Enables to precompute the suppliers without having to get a result set instance.
     *
     * @param prefix      prefix to add to the column names for the mapping (often used in combination with {@link #concatenateColumns(ColumnsConcatenationRequest)}).
     * @param columnNames result set column names (ordered).
     * @return the entity mapped (note that with a left join you can get an instance with only null fields).
     */
    Function<ResultSet, E> mapFromPrefix(String prefix, List<String> columnNames);

    Function<ResultSet, E> mapper(List<String> columns);

    Function<ResultSet, E> mapper(final ResultSet resultSet);

    interface ColumnMetadata {
        /**
         * @return {@code -1} if the column is not part of the identifier, else its natural index (integer starting at 0).
         */
        int idIndex();

        /**
         * @return {@code true} if the column is an identifer which is auto incremented.
         */
        boolean autoIncremented();

        String javaName();

        String columnName();

        Type type();

        /**
         * @param alias alias name in {@link #concatenateColumns(ColumnsConcatenationRequest)}.
         * @return the computed alias name for this column.
         */
        String toAliasName(String alias);
    }

    class ColumnsConcatenationRequest {
        /**
         * Prefix to prepend to column name.
         */
        private String prefix = "";

        /**
         * Prefix to use for aliasing, ignored if {@code null}.
         */
        private String aliasPrefix = null;

        /**
         * Ignored fields (either SQL column name or java field name).
         */
        private Set<String> ignored = Set.of();

        public String getPrefix() {
            return prefix;
        }

        public ColumnsConcatenationRequest setPrefix(final String prefix) {
            this.prefix = prefix;
            return this;
        }

        public String getAliasPrefix() {
            return aliasPrefix;
        }

        public ColumnsConcatenationRequest setAliasPrefix(final String aliasPrefix) {
            this.aliasPrefix = aliasPrefix;
            return this;
        }

        public Set<String> getIgnored() {
            return ignored;
        }

        public ColumnsConcatenationRequest setIgnored(final Set<String> ignored) {
            this.ignored = ignored;
            return this;
        }
    }
}
