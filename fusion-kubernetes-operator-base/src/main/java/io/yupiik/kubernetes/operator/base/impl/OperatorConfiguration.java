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
package io.yupiik.kubernetes.operator.base.impl;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;

@ApplicationScoped
@RootConfiguration("operator")
public record OperatorConfiguration(
        @Property(defaultValue = "true",
                documentation = "Enable operator watches and probes. Set explicitly to false in metadata-only containers.")
        boolean enabled,
        @Property(documentation = "Kubernetes client configuration.")
        Kubernetes kubernetes,

        @Property(value = "use-bookmarks", defaultValue = "true",
                documentation = "If `true`, `BOOKMARK` events are enabled.")
        boolean useBookmarks,

        @Property(value = "event-thread-count", defaultValue = "1",
                documentation = "How many threads are handling events, take care that more than one require a specific concurrency handling.")
        int eventThreadCount,

        @Property(defaultValue = "8081", value = "probe-port",
                documentation = "Server for healthchecks, set to a negative value to disable (when embedded for ex).")
        int probePort,

        @Property(defaultValue = "true",
                documentation = "Should operator await process termination, keep it `true` until you embed it.")
        boolean await,

        @Property(defaultValue = "null",
                documentation = "Directory for the last processed object inventory, used only to identify downtime deletions. Startup always lists current API state; no persistent cursor is resumed. Ignored if `null`.")
        String storage,

        @Property(documentation = "List/watch retry policy.")
        Retry retry
) {
    // Preserve the existing programmatic construction contract.
    public OperatorConfiguration(final Kubernetes kubernetes, final boolean useBookmarks, final int eventThreadCount,
                                 final int probePort, final boolean await, final String storage) {
        this(true, kubernetes, useBookmarks, eventThreadCount, probePort, await, storage);
    }

    public OperatorConfiguration(final boolean enabled, final Kubernetes kubernetes, final boolean useBookmarks,
                                 final int eventThreadCount, final int probePort, final boolean await, final String storage) {
        this(enabled, kubernetes, useBookmarks, eventThreadCount, probePort, await, storage, new Retry(100, 5_000, false));
    }

    public OperatorConfiguration {
        if (retry == null) {
            retry = new Retry(100, 5_000, false);
        }
    }

    public record Retry(
            @Property(value = "initial-delay", defaultValue = "100", documentation = "Initial retry delay in milliseconds, at least 1.")
            long initialDelay,
            @Property(value = "max-delay", defaultValue = "5000", documentation = "Maximum retry delay in milliseconds, including Retry-After.")
            long maxDelay,
            @Property(value = "retry-permanent-errors", defaultValue = "false", documentation = "Retry permanent list/watch failures (including 401, 403 and collection 404) instead of stopping the affected watch.")
            boolean retryPermanentErrors) {
        public Retry {
            if (initialDelay < 1 || maxDelay < initialDelay) {
                throw new IllegalArgumentException("Retry delays must satisfy 1 <= initial-delay <= max-delay");
            }
        }
    }

    public record Kubernetes(
            @Property(
                    documentation = "The kubernetes API base URL",
                    defaultValue = "java.util.Optional.ofNullable(System.getenv(\"KUBERNETES_SERVICE_HOST\"))" +
                            ".map(host -> \"https://\" + host + ':' + java.util.Optional.ofNullable(System.getenv(\"KUBERNETES_SERVICE_PORT\")).orElse(\"443\"))" +
                            ".orElse(\"https://kubernetes.default.svc\")")
            String master,

            @Property(
                    value = "tls-skip",
                    documentation = "Should TLS validations be skipped.",
                    defaultValue = "false")
            boolean skipTls,

            @Property(
                    documentation = "Kubernetes token (service account).",
                    defaultValue = "\"/var/run/secrets/kubernetes.io/serviceaccount/token\"")
            String token,

            @Property(
                    documentation = "Kubernetes certificate to connect to its API",
                    defaultValue = "\"/var/run/secrets/kubernetes.io/serviceaccount/ca.crt\"")
            String certificates) {
    }
}
