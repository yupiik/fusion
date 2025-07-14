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
                documentation = "Operator can store locally (on the filesystem) the latest resource version it saw. This must be a directory, ignored if `null`.")
        String storage
) {

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
