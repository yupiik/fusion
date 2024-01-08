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

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static java.util.Optional.ofNullable;

public class ExtendedHttpClient extends DelegatingHttpClient implements AutoCloseable {
    private final AtomicLong requestCounter;
    private final List<RequestListener<?>> listeners = new ArrayList<>();
    private final List<Consumer<ExtendedHttpClient>> onClose = new ArrayList<>();
    private final boolean isChild;

    public ExtendedHttpClient(final ExtendedHttpClientConfiguration clientConfiguration) {
        super(ofNullable(clientConfiguration.getDelegate()).orElseGet(HttpClient::newHttpClient));
        this.isChild = false;
        this.requestCounter = new AtomicLong();
        if (clientConfiguration.getRequestListeners() != null) {
            this.listeners.addAll(clientConfiguration.getRequestListeners());
        }
    }

    public ExtendedHttpClient(final ExtendedHttpClient extendedHttpClient) {
        super(extendedHttpClient.delegate);
        this.isChild = true;
        this.requestCounter = extendedHttpClient.requestCounter;
        this.listeners.addAll(extendedHttpClient.listeners);
    }

    public ExtendedHttpClient onClose(final Consumer<ExtendedHttpClient> task) {
        this.onClose.add(task);
        return this;
    }

    public long getRequestCount() {
        return requestCounter.get();
    }

    @Override
    public void close() {
        if (isChild) {
            return;
        }
        onClose.forEach(it -> it.accept(ExtendedHttpClient.this));
        listeners.stream()
                .filter(AutoCloseable.class::isInstance)
                .map(AutoCloseable.class::cast)
                .forEach(it -> {
                    try {
                        it.close();
                    } catch (final Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    private RequestListener.State<List<Object>> prepare(final HttpRequest request) {
        var req = request;
        final var states = new ArrayList<>(listeners.size());
        final var count = requestCounter.incrementAndGet();
        for (final RequestListener<?> l : listeners) {
            final var state = l.before(count, req);
            req = state.request();
            states.add(state.state());
        }
        return new RequestListener.State<>(req, states);
    }

    private <T> void after(final Iterator<Object> states, final HttpRequest request, final Throwable err, final HttpResponse<T> res) {
        for (final RequestListener<?> l : listeners) {
            if (!states.hasNext()) {// before was not called after, skip
                break;
            }
            final var looselyTyped = (RequestListener) l;
            looselyTyped.after(states.next(), request, err, res);
        }

    }

    public HttpResponse<String> send(final HttpRequest request) {
        try {
            return send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public HttpResponse<Path> getFile(final HttpRequest request, final Path target) {
        try {
            return send(request, HttpResponse.BodyHandlers.ofFile(target));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // for now only instrument synchronous calls
    @Override
    public <T> HttpResponse<T> send(final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
        final var prepare = prepare(request);
        final var state = prepare.state().iterator();
        try {
            final var res = super.send(prepare.request(), responseBodyHandler);
            after(state, request, null, res);
            return res;
        } catch (final RuntimeException | IOException re) {
            after(state, request, re, null);
            throw re;
        }
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler) {
        final var prepare = prepare(request);
        return super.sendAsync(prepare.request(), responseBodyHandler)
                .whenComplete((ok, ko) -> after(prepare.state().iterator(), request, ko, ok));
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler,
                                                            final HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        final var prepare = prepare(request);
        return super.sendAsync(prepare.request(), responseBodyHandler, pushPromiseHandler)
                .whenComplete((ok, ko) -> after(prepare.state().iterator(), request, ko, ok));
    }
}