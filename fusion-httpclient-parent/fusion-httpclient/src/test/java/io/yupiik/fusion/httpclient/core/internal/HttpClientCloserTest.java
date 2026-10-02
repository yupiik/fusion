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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpClientCloserTest {

    @Test
    void nullTimeoutLeavesTransportUntouched() throws Exception {
        // from the configuration javadoc: null/zero/negative means "transport left as-is"
        final var closed = new AtomicBoolean(false);
        final var client = new FakeClient(() -> {
            closed.set(true);
            return null;
        });
        HttpClientCloser.close(client, null);
        assertFalse(closed.get(), "null timeout must not close the transport");
        HttpClientCloser.close(client, Duration.ZERO);
        assertFalse(closed.get(), "zero timeout must not close the transport");
        HttpClientCloser.close(client, Duration.ofMillis(-1));
        assertFalse(closed.get(), "negative timeout must not close the transport");
    }

    @Test
    void positiveTimeoutClosesTheClient() throws Exception {
        final var closed = new AtomicBoolean(false);
        final var client = new FakeClient(() -> {
            closed.set(true);
            return null;
        });
        HttpClientCloser.close(client, Duration.ofMillis(1_000));
        assertTrue(closed.get(), "a positive timeout must close the transport");
    }

    @Test
    void positiveTimeoutWithHangingCloseDoesNotBlockForever() throws Exception {
        // a real JDK client close() blocks on idle keep-alive regardless of interruption
        // (there is no interruptible wait in HttpClientImpl#close), so the task stays
        // in the common pool and the caller must return as soon as the timeout elapses
        final var closed = new AtomicBoolean(false);
        final var client = new FakeClient(() -> {
            try {
                Thread.sleep(60_000);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            closed.set(true);
            return null;
        });
        final var start = System.nanoTime();
        HttpClientCloser.close(client, Duration.ofMillis(200)); // must return after ~200ms, not after 60s
        final var elapsed = System.nanoTime() - start;
        assertTrue(elapsed < Duration.ofSeconds(10).toNanos(), "close() must not block forever, took " + elapsed);
        // the important contract: the caller is not blocked; the pool task may still be running
        // (or already interrupted), the warning is what signals the failure to close
    }

    @Test
    void nonAutoCloseableClientIsIgnoredSilently() throws Exception {
        // Java 17 java.net.http.HttpClient does not implement AutoCloseable
        final var closed = new AtomicBoolean(false);
        HttpClientCloser.close(new NotAutoCloseable(), Duration.ofMillis(200));
        assertFalse(closed.get());
    }

    private interface CloseTask {
        Void run() throws Exception;
    }

    private static final class FakeClient extends java.net.http.HttpClient implements AutoCloseable {
        private final CloseTask closeTask;

        private FakeClient(final CloseTask closeTask) {
            this.closeTask = closeTask;
        }

        @Override
        public void close() throws Exception {
            closeTask.run();
        }

        // not used by the closer, minimal stubs to satisfy the abstract class
        @Override
        public java.util.Optional<java.net.CookieHandler> cookieHandler() {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<Duration> connectTimeout() {
            return java.util.Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public java.util.Optional<java.net.ProxySelector> proxy() {
            return java.util.Optional.empty();
        }

        @Override
        public javax.net.ssl.SSLContext sslContext() {
            return null;
        }

        @Override
        public javax.net.ssl.SSLParameters sslParameters() {
            return null;
        }

        @Override
        public java.util.Optional<java.net.Authenticator> authenticator() {
            return java.util.Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public java.util.Optional<java.util.concurrent.Executor> executor() {
            return java.util.Optional.empty();
        }

        @Override
        public <T> java.net.http.HttpResponse<T> send(final java.net.http.HttpRequest request,
                                                      final java.net.http.HttpResponse.BodyHandler<T> responseBodyHandler) {
            return null;
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(
                final java.net.http.HttpRequest request,
                final java.net.http.HttpResponse.BodyHandler<T> responseBodyHandler) {
            return null;
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(
                final java.net.http.HttpRequest request,
                final java.net.http.HttpResponse.BodyHandler<T> responseBodyHandler,
                final java.net.http.HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return null;
        }
    }

    private static final class NotAutoCloseable extends java.net.http.HttpClient {
        // all abstract methods stubbed to null/empty
        @Override
        public java.util.Optional<java.net.CookieHandler> cookieHandler() {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<Duration> connectTimeout() {
            return java.util.Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public java.util.Optional<java.net.ProxySelector> proxy() {
            return java.util.Optional.empty();
        }

        @Override
        public javax.net.ssl.SSLContext sslContext() {
            return null;
        }

        @Override
        public javax.net.ssl.SSLParameters sslParameters() {
            return null;
        }

        @Override
        public java.util.Optional<java.net.Authenticator> authenticator() {
            return java.util.Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public java.util.Optional<java.util.concurrent.Executor> executor() {
            return java.util.Optional.empty();
        }

        @Override
        public <T> java.net.http.HttpResponse<T> send(final java.net.http.HttpRequest request,
                                                      final java.net.http.HttpResponse.BodyHandler<T> responseBodyHandler) {
            return null;
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(
                final java.net.http.HttpRequest request,
                final java.net.http.HttpResponse.BodyHandler<T> responseBodyHandler) {
            return null;
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(
                final java.net.http.HttpRequest request,
                final java.net.http.HttpResponse.BodyHandler<T> responseBodyHandler,
                final java.net.http.HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return null;
        }
    }
}