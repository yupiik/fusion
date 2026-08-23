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
package io.yupiik.fusion.httpclient.core.listener.impl;

import io.yupiik.fusion.httpclient.core.listener.RequestListener;
import io.yupiik.fusion.httpclient.core.request.UnlockedHttpRequest;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toUnmodifiableSet;

/**
 * Usage:
 * <p>
 * {@code new CurlLoggerListener(new CurlLoggerListener.Configuration(Logger.getLogger(getClass().getName()))
 * .setLogPayload(clientConfiguration.isLogPayloads())
 * .setSensitiveHeaders(Set.of("authorization")))}
 * <p>
 * Common sensitive headers ({@link Configuration#DEFAULT_SENSITIVE_HEADERS}: authorization,
 * proxy-authorization, api-key, x-api-key, cookie, set-cookie, x-csrf-token) are masked
 * with {@code ******} unless {@code setSensitiveHeaders} is called.
 */
public class CurlLoggerListener implements RequestListener<CurlLoggerListener.Data> {
    private static final String MASK = "******";

    private final RuntimeConfiguration configuration;

    public CurlLoggerListener(final Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration required");
        final var headers = configuration.getSensitiveHeaders();
        this.configuration = new RuntimeConfiguration(
                configuration.getLogger(),
                configuration.isLogPayload(),
                headers.isEmpty() ? Set.of() :
                        headers.stream()
                                .map(name -> name.toLowerCase(Locale.ROOT))
                                .collect(toUnmodifiableSet()));
    }

    public static final class Configuration {
        /**
         * Header names masked by default when {@link #setSensitiveHeaders(Collection)} is not called.
         * Comparison is case insensitive.
         */
        public static final Set<String> DEFAULT_SENSITIVE_HEADERS = Set.of(
                "authorization", "proxy-authorization",
                "api-key", "x-api-key",
                "cookie", "set-cookie",
                "x-csrf-token");

        private final Logger logger;
        private boolean logPayload;
        private Collection<String> sensitiveHeaders = DEFAULT_SENSITIVE_HEADERS;

        public Configuration(final Logger logger) {
            this.logger = Objects.requireNonNull(logger, "logger required");
        }

        public Configuration setLogPayload(final boolean value) {
            this.logPayload = value;
            return this;
        }

        /**
         * Overrides {@link #DEFAULT_SENSITIVE_HEADERS}; pass an empty collection to disable masking.
         */
        public Configuration setSensitiveHeaders(final Collection<String> value) {
            this.sensitiveHeaders = value;
            return this;
        }

        private Logger getLogger() {
            return logger;
        }

        private boolean isLogPayload() {
            return logPayload;
        }

        private Collection<String> getSensitiveHeaders() {
            return sensitiveHeaders == null ? DEFAULT_SENSITIVE_HEADERS : sensitiveHeaders;
        }
    }

    private record RuntimeConfiguration(Logger logger, boolean logPayload, Set<String> sensitiveHeaders) {}

    @Override
    public State<Data> before(final long count, final HttpRequest request) {
        final var payload = readPayload(request);
        final var effectiveRequest = payload != null ?
                new UnlockedHttpRequest(
                        Optional.of(HttpRequest.BodyPublishers.ofByteArray(payload)),
                        request.method(),
                        request.timeout(),
                        request.expectContinue(),
                        request.uri(),
                        request.version(),
                        request.headers()) :
                request;
        return new State<>(effectiveRequest, new Data(payload));
    }

    @Override
    public void after(final Data before, final HttpRequest request, final Throwable error, final HttpResponse<?> response) {
        configuration.logger().info(() -> toLogMessage(request, before.payload, error, response));
    }

    protected String toLogMessage(final HttpRequest request, final byte[] payload, final Throwable error, final HttpResponse<?> response) {
        final var message = new StringBuilder(toCurl(request, payload));
        if (configuration.logPayload()) {
            message.append('\n');
            if (error != null) {
                message.append("curl: ").append(error.getMessage());
            } else if (response != null) {
                message.append(toResponse(response));
            }
        }
        return message.toString();
    }

    protected String toCurl(final HttpRequest request, final byte[] payload) {
        final var curl = new StringBuilder("curl");
        if (configuration.logPayload()) {
            curl.append(" -i");
        }
        curl.append(" -X ").append(request.method()).append(' ').append(quote(request.uri().toASCIIString()));
        request.headers().map().forEach((name, values) -> values.forEach(value ->
                curl.append(" \\\n  -H ").append(quote(name + ": " + mask(name, value)))));
        if (payload != null && payload.length > 0) {
            curl.append(" \\\n  --data-raw ").append(quote(new String(payload, UTF_8)));
        }
        return curl.toString();
    }

    private String toResponse(final HttpResponse<?> response) {
        final var builder = new StringBuilder("HTTP/").append(toHttpVersion(response.version())).append(' ').append(response.statusCode());
        if (response.headers() != null) {
            response.headers().map().forEach((name, values) -> values.forEach(value ->
                    builder.append('\n').append(name).append(": ").append(mask(name, value))));
        }
        builder.append("\n\n");
        if (response.body() != null) {
            builder.append(response.body());
        }
        return builder.toString();
    }

    private String toHttpVersion(final HttpClient.Version version) {
        return version == HttpClient.Version.HTTP_2 ? "2" : "1.1";
    }

    private String mask(final String name, final String value) {
        return configuration.sensitiveHeaders().contains(name.toLowerCase(Locale.ROOT)) ? MASK : value;
    }

    private String quote(final String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private byte[] readPayload(final HttpRequest request) {
        return request.bodyPublisher().map(p -> {
            if (p.contentLength() == 0) {
                return null;
            }
            final var subscriber = HttpResponse.BodySubscribers.ofByteArray();
            p.subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(final Flow.Subscription subscription) {
                    subscriber.onSubscribe(subscription);
                }

                @Override
                public void onNext(final ByteBuffer item) {
                    subscriber.onNext(List.of(item));
                }

                @Override
                public void onError(final Throwable throwable) {
                    subscriber.onError(throwable);
                }

                @Override
                public void onComplete() {
                    subscriber.onComplete();
                }
            });
            try {
                return subscriber.getBody().toCompletableFuture().get();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (final ExecutionException e) {
                throw new IllegalStateException(e.getCause());
            }
        }).orElse(null);
    }

    protected static class Data {
        private final byte[] payload;

        protected Data(final byte[] payload) {
            this.payload = payload;
        }
    }
}
