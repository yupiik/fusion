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
package io.yupiik.fusion.persistence.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static io.yupiik.fusion.persistence.api.StatementBinder.NONE;
import static java.util.Locale.ROOT;

/**
 * Helper class which aims at keeping sql build and binding linked for dynamic queries.
 * Ex:
 * <pre><code>
 *     final var findAllBuilder = new SqlBuilder(entity.getFindAllQuery() + " ");
 *     final var countAllBuilder = new SqlBuilder(entity.getCountAllQuery() + " ");
 *     if (!roles.contains("admin")) {
 *         Stream.of(findAllBuilder, countAllBuilder)
 *             .forEach(builder -> builder.append("WHERE user_id = ?", b -> b.bind(userId)));
 *     }
 *     final var items = findAllBuilder.visit((sql, binder) -> database.query(MyEntity.class, sql, binder));
 *     final long count = countAllBuilder.visit((sql, binder) -> database.query(sql, binder, BaseDatabase.FIRST_LONG));
 * </code></pre>
 */
public class SqlBuilder {
    private final List<String> sql = new ArrayList<>(2);
    private final List<Consumer<StatementBinder>> binders = new ArrayList<>();

    public SqlBuilder(final String sql) {
        this.sql.add(sql);
    }

    public SqlBuilder append(final String sql) {
        this.sql.add(sql);
        return this;
    }

    public SqlBuilder append(final String sql, final Consumer<StatementBinder> binder) {
        this.sql.add(sql);
        if (binder != NONE) {
            this.binders.add(binder);
        }
        return this;
    }

    public SqlBuilder appendWhere(final String joiningKeywordIfNotFirstWhere, final String sql,
                                  final Consumer<StatementBinder> binder) {
        return append(" " + (hasWhere() ? joiningKeywordIfNotFirstWhere : "WHERE") + " " + sql, binder);
    }

    public boolean hasWhere() {
        return hasSegmentStartingWith("where");
    }

    public boolean hasSegmentStartingWith(final String keyword) {
        return sql.stream().anyMatch(it -> {
            final var stripped = it.stripLeading();
            return stripped.length() > keyword.length() && stripped.substring(0, keyword.length()).toLowerCase(ROOT).startsWith(keyword);
        });
    }

    public SqlBuilder paginate(final int page, final int pageSize) {
        return append(
                " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                b -> b.bind(pageSize * (page - 1)).bind(pageSize));
    }

    public <T> T visit(final BiFunction<String, Consumer<StatementBinder>, T> applier) {
        return applier.apply(String.join(" ", sql), b -> binders.forEach(it -> it.accept(b)));
    }
}
