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

public record TomcatDatabaseConfiguration(
        String driver,
        String url,
        String username,
        String password,
        boolean testOnBorrow,
        boolean testOnReturn,
        boolean testWhileIdle,
        int timeBetweenEvictionRuns,
        int minEvictableIdleTime,
        String validationQuery,
        int validationQueryTimeout,
        int minIdle,
        int maxActive,
        boolean removeAbandoned,
        Boolean defaultAutoCommit,
        Boolean logAbandoned,
        int removeAbandonedTimeout,
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
