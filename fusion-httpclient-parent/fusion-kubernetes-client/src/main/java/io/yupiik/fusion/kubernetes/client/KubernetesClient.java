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
package io.yupiik.fusion.kubernetes.client;

import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.kubernetes.client.internal.LightYamlParser;
import io.yupiik.fusion.kubernetes.client.internal.PrivateKeyReader;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
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
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

public class KubernetesClient extends HttpClient implements AutoCloseable {
    private final HttpClient delegate;
    private final Path token;
    private final URI base;
    private final Clock clock = Clock.systemUTC(); // todo: enable to replace it through the config?
    private final Optional<String> namespace;
    private final Lock lock = new ReentrantLock();
    private volatile Instant lastRefresh;
    private volatile String authorization;

    public KubernetesClient(final KubernetesClientConfiguration configuration) {
        final var ns = fillFromKubeConfig(configuration.getKubeconfig(), configuration);
        this.base = URI.create(configuration.getMaster());
        this.delegate = ofNullable(configuration.getClient())
                .orElseGet(() -> ofNullable(configuration.getClientWrapper()).orElseGet(Function::identity).apply(createClient(configuration)));
        this.token = configuration.getToken() == null || "none".equals(configuration.getToken()) ? null : Paths.get(configuration.getToken());

        if (ns != null) {
            this.namespace = of(ns);
        } else {
            final var namespaceFile = Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/namespace");
            if (Files.exists(namespaceFile)) {
                try {
                    this.namespace = of(Files.readString(namespaceFile, StandardCharsets.UTF_8).strip());
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            } else {
                this.namespace = empty();
            }
        }
    }

    /**
     * @return the namespace configured in this POD - if {@code /var/run/secrets/kubernetes.io/serviceaccount/namespace exists}, empty otherwise.
     */
    public Optional<String> namespace() {
        return namespace;
    }


    /**
     * @return the base API url (https in general).
     */
    public URI base() {
        return base;
    }

    private HttpClient createClient(final KubernetesClientConfiguration configuration) {
        final var builder = HttpClient.newBuilder();
        if (!"http".equals(base.getScheme())) {
            builder.sslContext(createSSLContext(
                    configuration.isSkipTls(), configuration.getCertificates(),
                    configuration.getPrivateKey(), configuration.getPrivateKeyCertificate()));
        }
        if (configuration.getClientCustomizer() != null) {
            configuration.getClientCustomizer().accept(builder);
        }
        return builder.build();
    }

    private SSLContext createSSLContext(final boolean skipTls, final String certificates, final String key, final String keyCertificate) {
        byte[] data = null;
        if (certificates != null && certificates.contains("-BEGIN CERT")) {
            data = certificates.getBytes(StandardCharsets.UTF_8);
        } else {
            final var file = certificates == null ? null : Paths.get(certificates);
            if (file != null && Files.exists(file)) {
                try {
                    data = Files.readAllBytes(file);
                } catch (final IOException e) {
                    throw new IllegalArgumentException("Invalid certificate", e);
                }
            } else if (!skipTls) {
                try {
                    return SSLContext.getDefault();
                } catch (final NoSuchAlgorithmException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        try {
            final var ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);

            final TrustManager[] trustManagers;
            if (skipTls) {
                trustManagers = newNoopTrustManager();
            } else {
                final var certificateFactory = CertificateFactory.getInstance("X.509");
                try (final var caInput = new ByteArrayInputStream(data)) {
                    final var counter = new AtomicInteger();
                    final var certs = certificateFactory.generateCertificates(caInput);
                    certs.forEach(c -> {
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
                trustManagers = tmf.getTrustManagers();
            }

            final KeyManager[] keyManagers;
            if (key != null && !key.isBlank() && !"-".equals(key)) {
                final var privateKey = PrivateKeyReader.readPrivateKey(key.strip());
                try (final var certStream = new ByteArrayInputStream(keyCertificate.getBytes(StandardCharsets.UTF_8))) {
                    final var cert = (X509Certificate) CertificateFactory.getInstance("X509").generateCertificate(certStream);
                    ks.setKeyEntry(
                            cert.getSubjectX500Principal().getName(),
                            privateKey, new char[0], new Certificate[]{cert});
                    final var keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    keyManagerFactory.init(ks, new char[0]);
                    keyManagers = keyManagerFactory.getKeyManagers();
                }
            } else {
                keyManagers = null;
            }

            final var context = SSLContext.getInstance("TLSv1.2");
            context.init(keyManagers, trustManagers, null);
            return context;
        } catch (final IOException e) {
            throw new IllegalArgumentException("Invalid certificate/key: " + e.getMessage(), e);
        } catch (final KeyStoreException | CertificateException | NoSuchAlgorithmException | KeyManagementException |
                       UnrecoverableKeyException e) {
            throw new IllegalArgumentException("Can't create SSLContext: " + e.getMessage(), e);
        }
    }

    public HttpRequest prepare(final HttpRequest request) {
        final var uri = request.uri();
        final var actualUri = fixUri(uri, () -> base);
        final var builder = HttpRequest.newBuilder();
        builder.expectContinue(request.expectContinue());
        request.version().ifPresent(builder::version);
        builder.method(request.method(), request.bodyPublisher().orElseGet(HttpRequest.BodyPublishers::noBody));
        builder.uri(actualUri);
        request.timeout().ifPresent(builder::timeout);
        request.headers().map().forEach((k, v) -> v.forEach(it -> builder.header(k, it)));
        if (request.headers().firstValue("Authorization").isEmpty()) {
            final var auth = authorization();
            if (auth != null) {
                builder.header("Authorization", auth);
            }
        }
        return builder.build();
    }

    private String authorization() {
        final var now = clock.instant();
        if (isExpired(now)) {
            lock.lock();
            try {
                if (isExpired(now)) {
                    init();
                    this.lastRefresh = now;
                }
            } finally {
                lock.unlock();
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

    private void init() {
        if (token != null && Files.exists(token)) {
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
        return new WebSocketBuilderDelegate(delegate.newWebSocketBuilder()) {
            private boolean authorization;

            @Override
            public WebSocket.Builder header(final String name, final String value) {
                if (!authorization) {
                    authorization = "authorization".equalsIgnoreCase(name);
                }
                return super.header(name, value);
            }

            @Override
            public CompletableFuture<WebSocket> buildAsync(final URI uri, final WebSocket.Listener listener) {
                final var actualUri = fixUri(uri, () -> {
                    try {
                        return new URI("https".equals(base.getScheme()) ? "wss" : "ws", base.getUserInfo(), base.getHost(), base.getPort(), base.getPath(), base.getQuery(), base.getFragment());
                    } catch (final URISyntaxException e) {
                        throw new IllegalArgumentException(e);
                    }
                });
                if (!authorization) {
                    final var auth = authorization();
                    if (auth != null) {
                        header("Authorization", auth);
                    }
                }
                return super.buildAsync(actualUri, listener);
            }
        };
    }

    private TrustManager[] newNoopTrustManager() {
        return new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
                        // no-op
                    }

                    @Override
                    public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
                        // no-op
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };
    }

    private URI fixUri(final URI uri, final Supplier<URI> base) {
        return "kubernetes.api".equals(uri.getHost()) ?
                base.get().resolve(uri.getPath() + (uri.getRawQuery() == null || uri.getRawQuery().isBlank() ? "" : ("?" + uri.getRawQuery()))) :
                uri;
    }

    @SuppressWarnings("unchecked")
    private String fillFromKubeConfig(final Path kubeconfig, final KubernetesClientConfiguration configuration) {
        if (configuration.getKubeconfig() == null || Files.notExists(configuration.getKubeconfig())) {
            return null;
        }

        final var conf = loadKubeConfig(kubeconfig);
        final var currentContext = conf.get("current-context");
        if (!(currentContext instanceof String currentCtx)) {
            Logger.getLogger(getClass().getName()).warning(() -> "No current-context in '" + kubeconfig + "' skipping autoconfiguration of k8s connection.");
            return null;
        }

        final var contexts = conf.get("contexts");
        if (!(contexts instanceof List<?> ctxs)) {
            Logger.getLogger(getClass().getName()).warning(() -> "No contexts in '" + kubeconfig + "' skipping autoconfiguration of k8s connection.");
            return null;
        }

        final var ctx = ctxs.stream()
                .map(it -> (Map<String, Object>) it)
                .filter(it -> it.getOrDefault("name", "").equals(currentCtx))
                .findFirst()
                .map(it -> (Map<String, Object>) it.get("context"))
                .orElse(null);
        if (ctx == null) {
            Logger.getLogger(getClass().getName()).warning(() -> "No context '" + currentCtx + "' in '" + kubeconfig + "' skipping autoconfiguration of k8s connection.");
            return null;
        }

        final var namespace = ofNullable(ctx.get("namespace")).map(Object::toString).orElse(null);
        final var selectedCluster = findIn(ctx, "cluster", conf, "clusters");
        final var selectedUser = findIn(ctx, "user", conf, "users");

        selectedCluster
                .map(it -> it.get("server"))
                .map(Object::toString)
                .ifPresent(configuration::setMaster);
        selectedCluster
                .map(it -> {
                    try {
                        if (it.get("certificate-authority") instanceof String filePath) {
                            return Files.readString(Path.of(filePath));
                        }
                        if (it.get("certificate-authority-data") instanceof String data) {
                            return new String(Base64.getDecoder().decode(data), UTF_8);
                        }
                    } catch (final IOException ioe) {
                        throw new IllegalStateException(ioe);
                    }
                    return null;
                })
                .ifPresent(configuration::setCertificates);
        selectedCluster
                .map(it -> it.get("insecure-skip-tls-verify") instanceof String skip && Boolean.parseBoolean(skip))
                .ifPresent(configuration::setSkipTls);
        selectedUser
                .map(it -> {
                    try {
                        if (it.get("token") instanceof String data) {
                            return data;
                        }
                        if (it.get("tokenFile") instanceof String path) {
                            return Files.readString(Path.of(path));
                        }
                    } catch (final IOException ioe) {
                        throw new IllegalStateException(ioe);
                    }
                    return null;
                })
                .map(Object::toString)
                .ifPresent(configuration::setToken);
        selectedUser
                .map(it -> {
                    try {
                        if (it.get("client-key") instanceof String filePath) {
                            return Files.readString(Path.of(filePath));
                        }
                        if (it.get("client-key-data") instanceof String data) {
                            return new String(Base64.getDecoder().decode(data), UTF_8);
                        }
                    } catch (final IOException ioe) {
                        throw new IllegalStateException(ioe);
                    }
                    return null;
                })
                .map(Object::toString)
                .ifPresent(configuration::setPrivateKey);
        selectedUser
                .map(it -> {
                    try {
                        if (it.get("client-certificate") instanceof String filePath) {
                            return Files.readString(Path.of(filePath));
                        }
                        if (it.get("client-certificate-data") instanceof String data) {
                            return new String(Base64.getDecoder().decode(data), UTF_8);
                        }
                    } catch (final IOException ioe) {
                        throw new IllegalStateException(ioe);
                    }
                    return null;
                })
                .map(Object::toString)
                .ifPresent(configuration::setPrivateKeyCertificate);

        return namespace;
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> findIn(final Map<String, Object> ctx, final String attribute,
                                                 final Map<String, Object> conf, final String list) {
        return ofNullable(ctx.get(attribute))
                .map(Object::toString)
                .flatMap(cluster -> ofNullable(conf.get(list))
                        .map(it -> (List<Map<String, Object>>) it)
                        .flatMap(it -> it.stream()
                                .filter(c -> c.getOrDefault("name", "").equals(cluster))
                                .findFirst()))
                .map(it -> (Map<String, Object>) it.get(attribute));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadKubeConfig(final Path kubeconfig) {
        try (final var reader = Files.newBufferedReader(kubeconfig)) {
            if (kubeconfig.getFileName().toString().endsWith(".json")) {
                try (final var jsonMapper = new JsonMapperImpl(List.of(), k -> empty())) {
                    return (Map<String, Object>) jsonMapper.read(Object.class, reader);
                }
            }
            return (Map<String, Object>) new LightYamlParser().parse(reader);
        } catch (final IOException e) {
            throw new IllegalArgumentException("Can't parse kubeconfig: '" + kubeconfig + "'", e);
        }
    }
}
