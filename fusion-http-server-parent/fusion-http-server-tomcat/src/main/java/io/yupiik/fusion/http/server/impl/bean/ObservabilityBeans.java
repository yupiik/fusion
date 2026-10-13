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
package io.yupiik.fusion.http.server.impl.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.http.server.impl.observability.Health;
import io.yupiik.fusion.http.server.impl.observability.Metrics;
import io.yupiik.fusion.http.server.observability.HealthCheck;
import io.yupiik.fusion.http.server.observability.HealthRegistry;
import io.yupiik.fusion.http.server.observability.MetricsRegistry;
import io.yupiik.fusion.http.server.spi.MonitoringEndpoint;

import java.util.List;
import java.util.Map;

/**
 * Manual wiring of the health/metrics monitoring beans so they are registered without relying on the annotation
 * processor (the {@code fusion-http-server} module cannot use it due to the dependency cycle).
 */
public final class ObservabilityBeans {
    private ObservabilityBeans() {
        // no-op
    }

    public static final class HealthRegistryBean extends BaseBean<HealthRegistry> {
        public HealthRegistryBean() {
            super(HealthRegistry.class, ApplicationScoped.class, 1000, Map.of());
        }

        @Override
        public HealthRegistry create(final RuntimeContainer container, final List<Instance<?>> dependents) {
            return new HealthRegistry(lookups(
                    container, HealthCheck.class,
                    l -> l.stream().map(Instance::instance).toList(),
                    dependents));
        }
    }

    public static final class MetricsRegistryBean extends BaseBean<MetricsRegistry> {
        public MetricsRegistryBean() {
            super(MetricsRegistry.class, ApplicationScoped.class, 1000, Map.of());
        }

        @Override
        public MetricsRegistry create(final RuntimeContainer container, final List<Instance<?>> dependents) {
            return new MetricsRegistry();
        }
    }

    public abstract static class MonitoringEndpointBean extends BaseBean<MonitoringEndpoint> {
        protected MonitoringEndpointBean() {
            super(MonitoringEndpoint.class, ApplicationScoped.class, 100, Map.of());
        }
    }

    public static final class HealthMonitoringEndpointBean extends MonitoringEndpointBean {
        public HealthMonitoringEndpointBean() {
            super();
        }

        @Override
        public MonitoringEndpoint create(final RuntimeContainer container, final List<Instance<?>> dependents) {
            return new Health(lookup(container, HealthRegistry.class, dependents));
        }
    }

    public static final class MetricsMonitoringEndpointBean extends MonitoringEndpointBean {
        public MetricsMonitoringEndpointBean() {
            super();
        }

        @Override
        public MonitoringEndpoint create(final RuntimeContainer container, final List<Instance<?>> dependents) {
            return new Metrics(lookup(container, MetricsRegistry.class, dependents));
        }
    }
}