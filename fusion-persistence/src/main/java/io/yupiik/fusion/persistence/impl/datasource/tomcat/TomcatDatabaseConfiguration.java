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
package io.yupiik.fusion.persistence.impl.datasource.tomcat;

import io.yupiik.fusion.framework.build.api.configuration.Property;

// generic configuration which can be reused in a root configuration to create a database
public record TomcatDatabaseConfiguration(
        @Property(documentation = "JDBC driver to use to get connections.") String driver,
        @Property(documentation = "JDBC URL.") String url,
        @Property(documentation = "Database username.") String username,
        @Property(documentation = "Database password.") String password,
        @Property(documentation = "Should connections be tested when retrieved from the pool.", defaultValue = "false")
        boolean testOnBorrow,
        @Property(documentation = "Should connections be tested when returning to the pool.", defaultValue = "false")
        boolean testOnReturn,
        @Property(documentation = "Should connections be tested when not used and in the pool.", defaultValue = "true")
        boolean testWhileIdle,
        @Property(documentation = "Timeout (ms) between connection tests.", defaultValue = "5000")
        int timeBetweenEvictionRuns,
        @Property(documentation = "Timeout before any eviction test for a connection.", defaultValue = "60000")
        int minEvictableIdleTime,
        @Property(documentation = "Test query for eviction - if not set the driver one is used.")
        String validationQuery,
        @Property(documentation = "Default timeout for validations.", defaultValue = "30")
        int validationQueryTimeout,
        @Property(documentation = "Min number of connection - even when nothing happens.", defaultValue = "2")
        int minIdle,
        @Property(documentation = "Max active connections.", defaultValue = "100")
        int maxActive,
        @Property(documentation = "Should detected as abandonned connections be dropped.", defaultValue = "false")
        boolean removeAbandoned,
        @Property(documentation = "Should autocommit be used.") Boolean defaultAutoCommit,
        @Property(documentation = "Should abandons be logged.") Boolean logAbandoned,
        @Property(documentation = "Abandon timeout.", defaultValue = "60") int removeAbandonedTimeout,
        @Property(documentation = "Should a rollback be applied when returning to the pool.", defaultValue = "false")
        boolean rollbackOnReturn) {
    public TomcatDatabaseConfiguration(final String driver, final String url, final String username, final String password,
                                       final boolean testOnBorrow, final boolean testOnReturn, final boolean testWhileIdle,
                                       final int timeBetweenEvictionRuns, final int minEvictableIdleTime,
                                       final String validationQuery, final int validationQueryTimeout, final int minIdle,
                                       final int maxActive, final boolean removeAbandoned, final Boolean defaultAutoCommit,
                                       final Boolean logAbandoned, final int removeAbandonedTimeout) {
        this(
                driver, url, username, password, testOnBorrow, testOnReturn,
                testWhileIdle, timeBetweenEvictionRuns, minEvictableIdleTime, validationQuery, validationQueryTimeout,
                minIdle, maxActive, removeAbandoned, defaultAutoCommit, logAbandoned, removeAbandonedTimeout, false);
    }
}
