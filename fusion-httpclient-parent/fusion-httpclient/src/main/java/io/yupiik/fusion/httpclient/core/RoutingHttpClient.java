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
package io.yupiik.fusion.httpclient.core;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

/**
 * Enables to use a facade in front of multiple clients as a http client and switch based on the request metadata.
 *
 * @param <A> the type hosting the http clients this facade will switch between depending the request.
 */
public class RoutingHttpClient<A> extends HttpClient implements AutoCloseable {
    protected final A clients;
    protected final BiFunction<A, HttpRequest, HttpClient> selector;

    public RoutingHttpClient(final A clients, final BiFunction<A, HttpRequest, HttpClient> selector) {
        this.clients = clients;
        this.selector = selector;
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Redirect followRedirects() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<ProxySelector> proxy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SSLContext sslContext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SSLParameters sslParameters() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Version version() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Executor> executor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> HttpResponse<T> send(final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
        return selector.apply(clients, request).send(request, responseBodyHandler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler) {
        return selector.apply(clients, request).sendAsync(request, responseBodyHandler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler,
                                                            final HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        return selector.apply(clients, request).sendAsync(request, responseBodyHandler, pushPromiseHandler);
    }

    @Override
    public WebSocket.Builder newWebSocketBuilder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws Exception {
        if (clients instanceof AutoCloseable a) {
            a.close();
        }
    }

    /**
     * Creates a routed http client which will pick the client to use in a provided list.
     *
     * @param clients  clients to use (typically one with proxy+authenticator setup - proxied case - and one without).
     * @param selector the logic to pick the right client per request.
     * @return the routing http client.
     */
    public static RoutingHttpClient<List<HttpClient>> of(final List<HttpClient> clients,
                                                         final BiFunction<List<HttpClient>, HttpRequest, HttpClient> selector) {
        return new RoutingHttpClient<>(clients, selector) {
            @Override
            public void close() {
                clients.stream().filter(AutoCloseable.class::isInstance).map(AutoCloseable.class::cast).forEach(i -> {
                    try {
                        i.close();
                    } catch (final Exception e) {
                        // no-op
                    }
                });
            }
        };
    }
}
