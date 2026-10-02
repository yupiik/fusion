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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Closes an {@link HttpClient} without risking to hang forever on Java 17.
 * <p>
 * This "base" variant is compiled with {@code --release 17}: on this JDK the JDK client
 * neither implements {@link AutoCloseable} nor exposes {@code shutdown()},
 * {@code awaitTermination(Duration)} or {@code shutdownNow()} (they were added in Java 21).
 * The close is therefore attempted on the common pool to avoid blocking the caller, and
 * the wait is cancelled when {@code timeout} elapses if the client is not finished -
 * the underlying JDK threads are daemons and stop on their own once the client becomes
 * unreachable, so this is a best-effort graceful stop.
 * <p>
 * On Java 21+ the multi-release override of this class (in {@code META-INF/versions/21})
 * is loaded instead and uses the native {@code shutdown()}/{@code awaitTermination()}/{@code shutdownNow()}.
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
        if (!(client instanceof AutoCloseable closeable)) {
            return; // Java 17 client: nothing public can stop it, keep it silent and let it die with the JVM
        }
        final var future = ForkJoinPool.commonPool().submit(() -> {
            try {
                closeable.close();
            } catch (final Exception e) {
                LOGGER.warning(() -> "Error closing client: " + e.getMessage());
            }
        });
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (final TimeoutException e) {
            future.cancel(true); // the client was not finished in time, abandon the wait
            LOGGER.warning(() -> "HttpClient not closed after " + timeout);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
        } catch (final ExecutionException e) {
            LOGGER.warning(() -> "Error closing client: " + e.getCause().getMessage());
        }
    }
}