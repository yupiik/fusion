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
import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.event.Emitter;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.persistence.impl.DatabaseConfiguration;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// configuration as a bean to ensure it can be injected - at least to get the port
public class FusionDatabaseConfigurationBean extends BaseBean<DatabaseConfiguration> {
    public FusionDatabaseConfigurationBean() {
        super(DatabaseConfiguration.class, ApplicationScoped.class, 1000, Map.of());
    }

    @Override
    public DatabaseConfiguration create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        final var configuration = DatabaseConfiguration.of();

        try (final var instance = container.lookup(Emitter.class)) { // enable a listener to customize the configuration
            instance.instance().emit(configuration);
        }

        fillConfigurationIfNeeded(container, dependents, configuration);

        return configuration;
    }

    private void fillConfigurationIfNeeded(final RuntimeContainer container, final List<Instance<?>> dependents,
                                           final DatabaseConfiguration configuration) {
        if (configuration.getDataSource() == null) {
            // ensure it is optional to not fail if the module is not yet used
            // todo: revisit this hypothesis?
            ((Optional<DataSource>) lookup(
                    container,
                    new Types.ParameterizedTypeImpl(Optional.class, DataSource.class),
                    dependents))
                    .ifPresent(configuration::setDataSource);
        }
    }
}
