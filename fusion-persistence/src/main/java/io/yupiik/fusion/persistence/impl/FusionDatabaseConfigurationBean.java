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

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.event.Emitter;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.persistence.spi.DatabaseTranslation;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

// configuration as a bean to ensure it can be injected - at least to get the port
public class FusionDatabaseConfigurationBean extends BaseBean<DatabaseConfiguration> {
    public FusionDatabaseConfigurationBean() {
        super(FusionDatabaseConfigurationBean.class, ApplicationScoped.class, 1000, Map.of());
    }

    @Override
    public DatabaseConfiguration create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        final var configuration = DatabaseConfiguration.of();
        try (final var conf = container.lookup(Configuration.class)) {
            final var confAccessor = conf.instance();
            //confAccessor.get("fusion.http-server.port").map(Integer::parseInt).ifPresent(configuration::port);
        }

        try (final var instance = container.lookup(Emitter.class)) { // enable a listener to customize the configuration
            instance.instance().emit(configuration);
        }

        return configuration;
    }
}
