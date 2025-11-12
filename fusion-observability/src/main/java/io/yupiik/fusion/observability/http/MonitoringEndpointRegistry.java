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
package io.yupiik.fusion.http.server.observability.http;

import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.http.server.spi.Endpoint;
import io.yupiik.fusion.http.server.observability.health.Health;
import io.yupiik.fusion.http.server.observability.health.HealthRegistry;
import io.yupiik.fusion.http.server.observability.metrics.Metrics;
import io.yupiik.fusion.http.server.observability.metrics.MetricsRegistry;

import java.util.List;

@DefaultScoped
public class MonitoringEndpointRegistry {
    private final List<Endpoint> endpoints;

    public MonitoringEndpointRegistry(final HealthRegistry health, final MetricsRegistry metrics) {
        this.endpoints = List.of(new Health(health), new Metrics(metrics));
    }

    public List<Endpoint> endpoints() {
        return endpoints;
    }
}