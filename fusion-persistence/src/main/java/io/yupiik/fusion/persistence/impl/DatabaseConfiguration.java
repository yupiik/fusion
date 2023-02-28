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

import io.yupiik.fusion.persistence.impl.translation.DefaultTranslation;
import io.yupiik.fusion.persistence.impl.translation.H2Translation;
import io.yupiik.fusion.persistence.impl.translation.MySQLTranslation;
import io.yupiik.fusion.persistence.impl.translation.OracleTranslation;
import io.yupiik.fusion.persistence.impl.translation.PostgresTranslation;
import io.yupiik.fusion.persistence.spi.DatabaseTranslation;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.function.Function;

import static java.util.Locale.ROOT;
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
        if (translation == null && dataSource != null) {
            translation = guessTranslation();
        }
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

    /**
     * @return {@code this} instance, mainly used to ensure it can be passed to a
     * {@link io.yupiik.fusion.persistence.api.Database} instance.
     */
    public DatabaseConfiguration validate() {
        requireNonNull(dataSource, "No datasource set");
        return this;
    }

    private DatabaseTranslation guessTranslation() {
        try (final var connection = dataSource.getConnection()) {
            final var url = connection.getMetaData().getURL().toLowerCase(ROOT);
            if (url.contains("oracle")) {
                return new OracleTranslation();
            }
            if (url.contains("mariadb") || url.contains("mysql")) {
                return new MySQLTranslation();
            }
            if (url.contains("postgres")) {
                return new PostgresTranslation();
            }
            if (url.contains("jdbc:h2:") || url.contains("h2 database")) {
                return new H2Translation();
            }
            if (url.contains("cloudscape") || url.contains("idb") || url.contains("daffodil")) {
                return new DefaultTranslation();
            }
            /*
            if (url.contains("sqlserver")) {
                return dbdictionaryPlugin.unalias("sqlserver");
            }
            if (url.contains("jsqlconnect")) {
                return dbdictionaryPlugin.unalias("sqlserver");
            }
            if (url.contains("sybase")) {
                return dbdictionaryPlugin.unalias("sybase");
            }
            if (url.contains("adaptive server")) {
                return dbdictionaryPlugin.unalias("sybase");
            }
            if (url.contains("informix") || url.contains("ids")) {
                return dbdictionaryPlugin.unalias("informix");
            }
            if (url.contains("ingres")) {
                return dbdictionaryPlugin.unalias("ingres");
            }
            if (url.contains("hsql")) {
                return dbdictionaryPlugin.unalias("hsql");
            }
            if (url.contains("foxpro")) {
                return dbdictionaryPlugin.unalias("foxpro");
            }
            if (url.contains("interbase")) {
                return InterbaseDictionary.class.getName();
            }
            if (url.contains("jdatastore")) {
                return JDataStoreDictionary.class.getName();
            }
            if (url.contains("borland")) {
                return JDataStoreDictionary.class.getName();
            }
            if (url.contains("access")) {
                return dbdictionaryPlugin.unalias("access");
            }
            if (url.contains("pointbase")) {
                return dbdictionaryPlugin.unalias("pointbase");
            }
            if (url.contains("empress")) {
                return dbdictionaryPlugin.unalias("empress");
            }
            if (url.contains("firebird")) {
                return FirebirdDictionary.class.getName();
            }
            if (url.contains("cache")) {
                return CacheDictionary.class.getName();
            }
            if (url.contains("derby")) {
                return dbdictionaryPlugin.unalias("derby");
            }
            if (url.contains("sapdb")) {
                return dbdictionaryPlugin.unalias("maxdb");
            }
            if (url.contains("herddb")) {
                return dbdictionaryPlugin.unalias("herddb");
            }
            if (url.contains("db2") || url.contains("as400")) {
                return dbdictionaryPlugin.unalias("db2");
            }
            if (url.contains("soliddb")) {
                return dbdictionaryPlugin.unalias("soliddb");
            }
            */
            throw new IllegalArgumentException("" +
                    "Unknown database: '" + url + "'. " +
                    "Can't find a database translation to use, set it in the configuration. " +
                    "If you are not sure, you can start by setting `io.yupiik.fusion.persistence.impl.translation.DefaultTranslation`");
        } catch (final SQLException ex) {
            throw new IllegalArgumentException("Cant find database translation, probably set it in the configuration", ex);
        }
    }

    public static DatabaseConfiguration of() {
        return new DatabaseConfiguration();
    }
}
