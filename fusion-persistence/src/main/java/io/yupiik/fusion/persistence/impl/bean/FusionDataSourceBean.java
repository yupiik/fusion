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
package io.yupiik.fusion.persistence.impl.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.persistence.impl.datasource.tomcat.TomcatDataSource;
import io.yupiik.fusion.persistence.impl.datasource.tomcat.TomcatDatabaseConfiguration;

import java.util.List;
import java.util.Map;

public class FusionDataSourceBean extends BaseBean<TomcatDataSource> {
    public FusionDataSourceBean() {
        super(TomcatDataSource.class, ApplicationScoped.class, 0, Map.of());
    }

    @Override
    public TomcatDataSource create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        try (final var confInstance = container.lookup(Configuration.class)) {
            final var conf = confInstance.instance();
            final var urlConf = conf.get("fusion.persistence.datasource.url");
            if (urlConf.isEmpty()) {
                throw new IllegalArgumentException("No TomcatDataSource configuration, `fusion.persistence.datasource.url`.");
            }
            final var databaseConfiguration = new TomcatDatabaseConfiguration(
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
                    conf.get("fusion.persistence.datasource.removeAbandonedTimeout").map(Integer::parseInt).orElse(60));
            return new TomcatDataSource(databaseConfiguration);
        }
    }

    @Override
    public void destroy(final RuntimeContainer container, final TomcatDataSource instance) {
        instance.close();
    }
}
