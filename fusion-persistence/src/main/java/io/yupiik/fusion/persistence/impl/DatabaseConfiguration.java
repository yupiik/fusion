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

import io.yupiik.fusion.persistence.spi.DatabaseTranslation;

import javax.sql.DataSource;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class DatabaseConfiguration {
    private Function<Class<?>, Object> instanceLookup;
    private DataSource dataSource;
    private DatabaseTranslation translation;

    public Function<Class<?>, Object> getInstanceLookup() {
        return instanceLookup;
    }

    public DatabaseConfiguration setInstanceLookup(final Function<Class<?>, Object> instanceLookup) {
        this.instanceLookup = instanceLookup;
        return this;
    }

    public DatabaseTranslation getTranslation() {
        return translation;
    }

    public DatabaseConfiguration setTranslation(final DatabaseTranslation translation) {
        this.translation = translation;
        return this;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public DatabaseConfiguration setDataSource(final DataSource dataSource) {
        this.dataSource = dataSource;
        return this;
    }

    public void validate() {
        requireNonNull(dataSource, "No datasource set");
    }

    static DatabaseConfiguration of() {
        return new DatabaseConfiguration();
    }
}
