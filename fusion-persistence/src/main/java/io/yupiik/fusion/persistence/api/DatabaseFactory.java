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

import io.yupiik.fusion.persistence.impl.DatabaseConfiguration;

/**
 * Bean enabling to create a custom unmanaged database.
 * Can be created in a @{@link io.yupiik.fusion.framework.build.api.scanning.Bean} method.
 */
public interface DatabaseFactory {
    /**
     * Create a database with this particular configuration.
     * If instance lookup is not set (common case) entities are looked up in the container.
     * <p>
     * IMPORTANT: don't forget to close it to release dependent beans if you don't use it with
     * a @{@link io.yupiik.fusion.framework.build.api.scanning.Bean} method (where it is automatically closed).
     *
     * @param configuration configuration to use for this database instance.
     * @return the database.
     */
    CloseableDatabase create(DatabaseConfiguration configuration);

    interface CloseableDatabase extends Database, AutoCloseable {
    }
}
