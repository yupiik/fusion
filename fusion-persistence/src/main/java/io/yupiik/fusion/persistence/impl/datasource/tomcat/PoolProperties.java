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

import io.yupiik.fusion.framework.api.configuration.Configuration;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;

public final class PoolProperties {
    private PoolProperties() {
        // no-op
    }

    public static PoolConfiguration toProperties(final Configuration conf) {
        final var urlConf = conf.get("fusion.persistence.datasource.url");
        if (urlConf.isEmpty()) {
            throw new IllegalArgumentException("No TomcatDataSource configuration, `fusion.persistence.datasource.url`.");
        }
        return toProperties(new TomcatDatabaseConfiguration(
                conf.get("fusion.persistence.datasource.driver").orElse(null),
                urlConf.orElseThrow(),
                conf.get("fusion.persistence.datasource.username").orElse(null),
                conf.get("fusion.persistence.datasource.password").orElse(null),
                conf.get("fusion.persistence.datasource.testOnBorrow").map(Boolean::parseBoolean).orElse(false),
                conf.get("fusion.persistence.datasource.testOnReturn").map(Boolean::parseBoolean).orElse(false),
                conf.get("fusion.persistence.datasource.testWhileIdle").map(Boolean::parseBoolean).orElse(true),
                conf.get("fusion.persistence.datasource.timeBetweenEvictionRuns").map(Integer::parseInt).orElse(5000),
                conf.get("fusion.persistence.datasource.minEvictableIdleTime").map(Integer::parseInt).orElse(60000),
                conf.get("fusion.persistence.datasource.validationQuery").orElse(null),
                conf.get("fusion.persistence.datasource.validationQueryTimeout").map(Integer::parseInt).orElse(-1),
                conf.get("fusion.persistence.datasource.minIdle").map(Integer::parseInt).orElse(2),
                conf.get("fusion.persistence.datasource.maxActive").map(Integer::parseInt).orElse(100),
                conf.get("fusion.persistence.datasource.removeAbandoned").map(Boolean::parseBoolean).orElse(false),
                conf.get("fusion.persistence.datasource.defaultAutoCommit").map(Boolean::parseBoolean).orElse(null),
                conf.get("fusion.persistence.datasource.logAbandoned").map(Boolean::parseBoolean).orElse(false),
                conf.get("fusion.persistence.datasource.removeAbandonedTimeout").map(Integer::parseInt).orElse(60)));
    }

    public static PoolConfiguration toProperties(final TomcatDatabaseConfiguration db) {
        final var properties = new org.apache.tomcat.jdbc.pool.PoolProperties();
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
}
