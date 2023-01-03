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
package io.yupiik.fusion.kubernetes.client;

import java.net.http.HttpClient;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

public class KubernetesClientConfiguration {
    private HttpClient client;
    private Function<HttpClient, HttpClient> clientWrapper;
    private String master = ofNullable(System.getenv("KUBERNETES_SERVICE_HOST"))
            .map(host -> "https://" + host + ':' + ofNullable(System.getenv("KUBERNETES_SERVICE_PORT")).orElse("443"))
            .orElse(null);
    private String token = "/var/run/secrets/kubernetes.io/serviceaccount/token";
    private String certificates = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt";

    public Function<HttpClient, HttpClient> getClientWrapper() {
        return clientWrapper;
    }

    /**
     * @param clientWrapper function which will wrap the automatically created kubernetes client (when {@code client} is null in the configuration).
     * @return this configuration.
     */
    public KubernetesClientConfiguration setClientWrapper(final Function<HttpClient, HttpClient> clientWrapper) {
        this.clientWrapper = clientWrapper;
        return this;
    }

    public String getMaster() {
        return master;
    }

    public KubernetesClientConfiguration setMaster(final String master) {
        this.master = master;
        return this;
    }

    public String getToken() {
        return token;
    }

    /**
     * @param token path of the token file to read.
     * @return the token file path.
     */
    public KubernetesClientConfiguration setToken(final String token) {
        this.token = token;
        return this;
    }

    public String getCertificates() {
        return certificates;
    }

    /**
     * @param certificates path of the PEM certificates file to read.
     * @return path of the PEM certificates.
     */
    public KubernetesClientConfiguration setCertificates(final String certificates) {
        this.certificates = certificates;
        return this;
    }

    public HttpClient getClient() {
        return client;
    }

    public KubernetesClientConfiguration setClient(final HttpClient client) {
        this.client = client;
        return this;
    }
}
