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
package io.yupiik.fusion.httpclient.core;

import io.yupiik.fusion.httpclient.core.listener.RequestListener;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

public class ExtendedHttpClientConfiguration {
    private HttpClient delegate;
    private List<RequestListener<?>> requestListeners = List.of();
    private Duration closeTimeout;

    public ExtendedHttpClientConfiguration setDelegate(final HttpClient delegate) {
        this.delegate = delegate;
        return this;
    }

    public ExtendedHttpClientConfiguration setRequestListeners(final List<RequestListener<?>> requestListeners) {
        this.requestListeners = requestListeners;
        return this;
    }

    /**
     * Maximum time to wait for the underlying {@link HttpClient} to terminate when {@link ExtendedHttpClient#close()} is called.
     * <p>
     * When {@code null} (the default), the transport is always left as-is on close: it is only closed when no delegate
     * was provided and this timeout is set - this preserves the historical behavior where only listeners and hooks
     * are closed.
     * <p>
     * When greater than zero and no delegate is provided (the client owns the transport):
     * <ul>
     *     <li>on Java 21+, {@code shutdown()} is called, then {@code awaitTermination(closeTimeout)}; if the client is
     *     not terminated after the timeout, {@code shutdownNow()} is invoked to force the termination</li>
     *     <li>on Java 17, the JDK client exposes no shutdown/termination API, so the close is attempted on the common
     *     pool and the wait is abandoned (cancelled) when the timeout elapses - this is a best effort, the underlying
     *     threads are daemons and stop when the client becomes unreachable</li>
     * </ul>
     * A value of zero or negative disables the bounded wait and leaves the transport as-is (default behavior).
     */
    public ExtendedHttpClientConfiguration setCloseTimeout(final Duration closeTimeout) {
        this.closeTimeout = closeTimeout;
        return this;
    }

    public Duration getCloseTimeout() {
        return closeTimeout;
    }

    public HttpClient getDelegate() {
        return delegate;
    }

    public List<RequestListener<?>> getRequestListeners() {
        return requestListeners;
    }
}
