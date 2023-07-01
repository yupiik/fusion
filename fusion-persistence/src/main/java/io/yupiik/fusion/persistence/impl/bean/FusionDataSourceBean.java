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
import io.yupiik.fusion.persistence.impl.datasource.tomcat.SimpleTomcatDataSource;
import io.yupiik.fusion.persistence.impl.datasource.tomcat.ThreadLocalTomcatDataSource;
import io.yupiik.fusion.persistence.impl.datasource.tomcat.TomcatDataSource;

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
            if (conf.get("fusion.persistence.contextLess").map(Boolean::parseBoolean).orElse(false)) {
                return new SimpleTomcatDataSource(conf);
            }
            return new ThreadLocalTomcatDataSource(conf);
        }
    }

    @Override
    public void destroy(final RuntimeContainer container, final TomcatDataSource instance) {
        instance.close();
    }
}
