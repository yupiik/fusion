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
package io.yupiik.fusion.httpclient.core.internal;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Runs in the probe JVM of {@link HttpClientCloserJava21Test} with the multi-release jar
 * on the classpath: the JVM resolves {@link HttpClientCloser} to the META-INF/versions/21
 * variant and the calls below are direct (non reflective) so no module access issue occurs.
 */
public final class ProbeMain {
    private ProbeMain() {
        // no-op
    }

    public static void main(final String[] args) throws Exception {
        // server that accepts then never responds: keeps a request in flight
        try (final var server = new java.net.ServerSocket()) {
            server.bind(new java.net.InetSocketAddress("localhost", 0));
            final var client = HttpClient.newHttpClient();
            final var inFlight = client.sendAsync(
                    HttpRequest.newBuilder()
                            .uri(java.net.URI.create("http://localhost:" + server.getLocalPort() + "/never"))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            final long start = System.nanoTime();
            HttpClientCloser.close(client, Duration.ofMillis(200));
            final long elapsed = System.nanoTime() - start;
            System.out.println("close-fast=" + (elapsed < Duration.ofSeconds(5).toNanos()));
            System.out.println("terminated=" + isTerminated(client));
            inFlight.cancel(true);

            final var noTimeout = HttpClient.newHttpClient();
            HttpClientCloser.close(noTimeout, null);
            System.out.println("no-timeout-terminated=" + isTerminated(noTimeout));
            HttpClientCloser.close(noTimeout, Duration.ofSeconds(1));
        }
    }

    private static boolean isTerminated(final HttpClient client) throws Exception {
        return (boolean) HttpClient.class.getMethod("isTerminated").invoke(client);
    }
}