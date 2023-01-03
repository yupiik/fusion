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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;

public class KubernetesClient extends HttpClient implements AutoCloseable {
    private final HttpClient delegate;
    private final Path token;
    private final URI base;
    private final Clock clock = Clock.systemUTC(); // todo: enable to replace it through the config?
    private final Optional<String> namespace;
    private volatile Instant lastRefresh;
    private volatile String authorization;

    public KubernetesClient(final KubernetesClientConfiguration configuration) {
        this.delegate = ofNullable(configuration.getClient())
                .orElseGet(() -> ofNullable(configuration.getClientWrapper()).orElseGet(Function::identity).apply(createClient(configuration)));
        this.token = Paths.get(configuration.getToken());
        this.base = URI.create(configuration.getMaster());

        final var namespaceFile = Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/namespace");
        if (Files.exists(namespaceFile)) {
            try {
                this.namespace = ofNullable(Files.readString(namespaceFile, StandardCharsets.UTF_8).strip());
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        } else {
            this.namespace = empty();
        }
    }

    /**
     * @return the namespace configured in this POD - if {@code /var/run/secrets/kubernetes.io/serviceaccount/namespace exists}, empty otherwise.
     */
    public Optional<String> namespace() {
        return namespace;
    }

    private HttpClient createClient(final KubernetesClientConfiguration configuration) {
        return HttpClient.newBuilder()
                .sslContext(createSSLContext(configuration.getCertificates()))
                .build();
    }

    private SSLContext createSSLContext(final String certificates) {
        final var file = Paths.get(certificates);
        if (!Files.exists(file)) {
            try {
                return SSLContext.getDefault();
            } catch (final NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
        try {
            final var data = Files.readAllBytes(file);

            final var ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);

            final var certificateFactory = CertificateFactory.getInstance("X.509");
            try (final var caInput = new ByteArrayInputStream(data)) {
                final var counter = new AtomicInteger();
                certificateFactory.generateCertificates(caInput).forEach(c -> {
                    try {
                        ks.setCertificateEntry("ca-" + counter.incrementAndGet(), c);
                    } catch (final KeyStoreException e) {
                        throw new IllegalArgumentException(e);
                    }
                });
            } catch (final CertificateException | IOException e) {
                throw new IllegalArgumentException(e);
            }

            final var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            final var context = SSLContext.getInstance("TLSv1.2");
            context.init(null, tmf.getTrustManagers(), null);
            return context;
        } catch (final IOException e) {
            throw new IllegalArgumentException("Invalid certificate: " + e.getMessage(), e);
        } catch (final KeyStoreException | CertificateException | NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalArgumentException("Can't create SSLContext: " + e.getMessage(), e);
        }
    }

    private HttpRequest prepare(final HttpRequest request) {
        final var uri = request.uri();
        final var actualUri = "kubernetes.api".equals(uri.getHost()) ?
                base.resolve(uri.getPath() + (uri.getRawQuery() == null || uri.getRawQuery().isBlank() ? "" : ("?" + uri.getRawQuery()))) :
                uri;
        final var builder = HttpRequest.newBuilder();
        builder.expectContinue(request.expectContinue());
        request.version().ifPresent(builder::version);
        builder.method(request.method(), request.bodyPublisher().orElseGet(HttpRequest.BodyPublishers::noBody));
        builder.uri(actualUri);
        request.timeout().ifPresent(builder::timeout);
        request.headers().map().forEach((k, v) -> v.forEach(it -> builder.header(k, it)));
        if (request.headers().firstValue("Authorization").isEmpty()) {
            builder.header("Authorization", authorization());
        }
        return builder.build();
    }

    private String authorization() {
        final var now = clock.instant();
        if (isExpired(now)) {
            synchronized (this) {
                if (isExpired(now)) {
                    init();
                    this.lastRefresh = now;
                }
            }
        }
        return authorization;
    }

    private boolean isExpired(final Instant instant) {
        if (lastRefresh == null) {
            return true;
        }
        return lastRefresh.isBefore(instant.minusSeconds(60));
    }

    private void init() { // todo: ignore if not there? log? weird case: mocked k8s without this need
        if (Files.exists(token)) {
            try {
                authorization = "Bearer " + Files.readString(token, StandardCharsets.UTF_8).strip();
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    public void close() {
        // no-op for now
    }

    // enables to not handle exceptions in caller code
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

    @Override
    public <T> HttpResponse<T> send(final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
        return delegate.send(prepare(request), responseBodyHandler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler) {
        return delegate.sendAsync(prepare(request), responseBodyHandler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler,
                                                            final HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        return delegate.sendAsync(prepare(request), responseBodyHandler, pushPromiseHandler);
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return delegate.cookieHandler();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return delegate.connectTimeout();
    }

    @Override
    public Redirect followRedirects() {
        return delegate.followRedirects();
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return delegate.proxy();
    }

    @Override
    public SSLContext sslContext() {
        return delegate.sslContext();
    }

    @Override
    public SSLParameters sslParameters() {
        return delegate.sslParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return delegate.authenticator();
    }

    @Override
    public Version version() {
        return delegate.version();
    }

    @Override
    public Optional<Executor> executor() {
        return delegate.executor();
    }

    @Override
    public WebSocket.Builder newWebSocketBuilder() {
        return delegate.newWebSocketBuilder();
    }
}
