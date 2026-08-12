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
package io.yupiik.fusion.http.server.observability;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry of {@link HealthCheck} instances rendered by the {@code /health} monitoring endpoint.
 */
public class HealthRegistry {
    private final List<HealthCheck> healthChecks;

    public HealthRegistry(final List<HealthCheck> healthChecks) {
        this.healthChecks = healthChecks == null ? null : new CopyOnWriteArrayList<>(healthChecks);
    }

    public Removable register(final HealthCheck check) {
        healthChecks.add(check);
        return () -> healthChecks.remove(check);
    }

    public List<HealthCheck> healthChecks() {
        return healthChecks;
    }

    public interface Removable extends AutoCloseable {
        @Override
        void close();
    }
}