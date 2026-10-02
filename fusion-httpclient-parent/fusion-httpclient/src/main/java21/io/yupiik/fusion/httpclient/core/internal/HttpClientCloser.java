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
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Closes an {@link HttpClient} on Java 21+ without risking to hang forever.
 * <p>
 * This is the multi-release override of the class compiled with {@code --release 17}:
 * it is packaged in {@code META-INF/versions/21} and loaded only on Java 21+.
 * It relies on the JDK 21 shutdown API: {@link HttpClient#shutdown()} starts a graceful
 * shutdown, {@link HttpClient#awaitTermination(Duration)} waits up to {@code timeout}
 * and {@link HttpClient#shutdownNow()} is only called when the timeout elapsed without
 * termination (it aborts pending operations and does not throw, see the JDK implementation).
 */
public final class HttpClientCloser {
    private static final Logger LOGGER = Logger.getLogger(HttpClientCloser.class.getName());

    private HttpClientCloser() {
        // no-op
    }

    /**
     * Closes the given client, waiting at most {@code timeout} for the termination.
     *
     * @param client  the client to close; ignored unless it implements {@link AutoCloseable} and {@code timeout} is positive
     * @param timeout the maximum time to wait; if {@code null} or non positive the transport is left as-is
     *                (historical behavior: {@link ExtendedHttpClient#close()} never closes the transport by default)
     */
    public static void close(final HttpClient client, final Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return; // only a positive timeout opts in to closing the transport
        }
        if (!(client instanceof AutoCloseable)) {
            return; // not a JDK 21 client, nothing to stop
        }
        client.shutdown();
        try {
            if (!client.awaitTermination(timeout)) {
                client.shutdownNow(); // does not throw even with in-flight requests
                LOGGER.warning(() -> "HttpClient not terminated after " + timeout + ", forced shutdown");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            client.shutdownNow();
            LOGGER.warning(() -> "Interrupted while waiting for HttpClient termination");
        }
    }
}