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

import java.sql.Connection;
import java.util.function.Function;
import java.util.function.Supplier;

public interface TransactionManager {
    <T> T write(Function<Connection, T> function);

    <T> T writeSQL(SQLFunction<Connection, T> function);

    <T> T read(Function<Connection, T> function);

    <T> T readSQL(SQLFunction<Connection, T> function);

    /**
     * Binds a connection to current thread in read-only mode, the result will be rolled-backed if needed.
     *
     * @param supplier the task to execute.
     * @param <T>      the returned type.
     * @return the result of the function computation.
     */
    default <T> T read(final Supplier<T> supplier) {
        return this.read(c -> supplier.get());
    }

    /**
     * Binds a connection to current thread in write mode, the result will be committed if there is no error.
     *
     * @param supplier the task to execute.
     * @param <T>      the returned type.
     * @return the result of the function computation.
     */
    default <T> T write(final Supplier<T> supplier) {
        return this.write(c -> supplier.get());
    }
}
