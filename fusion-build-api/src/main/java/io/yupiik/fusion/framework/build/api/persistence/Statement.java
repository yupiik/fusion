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
package io.yupiik.fusion.framework.build.api.persistence;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Mark an interface as owning operations.
 */
@Target(METHOD)
@Retention(SOURCE)
public @interface Statement {
    /**
     * SQL to use for this statement, it can use @{@link Operation#aliases()} mappings.
     * Supported placeholders (values surrounded by {@code ${}) are:
     *
     * <ul>
     *     <li>{@code <alias or fqn>#fields}: all fields of the entity (kind of equivalent to {@code *} in plain SQL).</li>
     *     <li>{@code <alias or fqn>#table}: table name from @{@link io.yupiik.fusion.framework.build.api.persistence.Table} or convention.</li>
     * </ul>
     * <p>
     * You can also use name parameter with placeholders like {@code parameters#name}, it uses bytecode parameter names
     * so ensure to set {@code -parameters} to the java compiler.
     *
     * The placeholder {@code ${parameters#<name>#in}} can be used to expand a collection to a dynamic number of {@code ?}.
     * For example: {@code select ${e#fields} from ${e#table} where name in ${parameters#name#in}}.
     *
     * IMPORTANT: if you use {@code ${parameters#<name>}} bindings, don't mix it with manual {@code ?}, it will not work,
     * use either only manual explicit {@code ?} bindings or placeholder ones.
     *
     * @return the SQL.
     */
    String value();
}
