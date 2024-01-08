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

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.fusion.httpclient.core.listener.RequestListener;
import io.yupiik.fusion.httpclient.core.request.UnlockedHttpRequest;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

public abstract class BaseHARDumperListener implements RequestListener<BaseHARDumperListener.Data> {
    protected final BaseConfiguration<?> configuration;

    public BaseHARDumperListener(final BaseConfiguration<?> configuration) {
        this.configuration = configuration;
    }

    @Override
    public State<Data> before(final long count, final HttpRequest request) {
        final var requestPayload = readRequestPayload(request);
        if (requestPayload == null) {
            return new State<>(request, new Data(configuration.clock.instant(), null));
        }
        return new State<>(
                new UnlockedHttpRequest(
                        Optional.of(HttpRequest.BodyPublishers.ofByteArray(requestPayload)),
                        request.method(),
                        request.timeout(),
                        request.expectContinue(),
                        request.uri(),
                        request.version(),
                        request.headers()),
                new Data(configuration.clock.instant(), requestPayload));
    }

    @Override
    public void after(final Data state, final HttpRequest request, final Throwable error, final HttpResponse<?> response) {
        onEntry(new Har.Entry(
                configuration.enableStartedDateTime ? state.instant.atZone(configuration.clock.getZone()) : null,
                null,
                configuration.enableTime ? Duration.between(state.instant, configuration.clock.instant()).toMillis() : null,
                toRequest(request, state.requestPayload),
                toResponse(response),
                null, null, null, null, ""));
    }

    protected abstract void onEntry(final Har.Entry entry);

    private Har.Request toRequest(final HttpRequest request, final byte[] body) {
        final var headers = toHeaders(request.headers());
        final var url = request.uri().toASCIIString();
        return new Har.Request(
                request.method(),
                request.uri().getQuery() != null ? url.substring(0, url.length() - request.uri().getQuery().length()) : url,
                request.version().map(this::toHttpVersion).orElse("HTTP/1.1"),
                null,
                headers,
                ofNullable(request.uri().getQuery())
                        .filter(it -> !it.isBlank())
                        .map(query -> Stream.of(query.split("&"))
                                .map(it -> {
                                    final int eq = it.indexOf('=');
                                    if (eq > 0) {
                                        return new String[]{it.substring(0, eq), it.substring(eq + 1)};
                                    }
                                    return new String[]{it, ""};
                                })
                                .map(arr -> new Har.Query(arr[0], arr[1], ""))
                                .collect(toList()))
                        .orElse(null),
                body != null ?
                        request.headers()
                                .firstValue("Content-Type")
                                .filter(contentType -> contentType.contains("form"))
                                .map(contentType -> new Har.PostData(
                                        contentType,
                                        Stream.of(new String(body, StandardCharsets.UTF_8).split("&"))
                                                .map(it -> {
                                                    final var eq = it.indexOf('=');
                                                    return eq > 0 ?
                                                            new Har.Param(it.substring(0, eq), it.substring(eq + 1), null, null, "") :
                                                            new Har.Param(it, "", null, null, "");
                                                })
                                                .collect(toList()),
                                        null, ""))
                                .orElseGet(() -> new Har.PostData(null, null, new String(body, StandardCharsets.UTF_8), "")) :
                        null,
                toHeaderSize(headers),
                body != null ? body.length : -1,
                "");
    }

    private String toHttpVersion(final HttpClient.Version it) {
        if (it == null) {
            return "HTTP/1.1";
        }
        return switch (it) {
            case HTTP_2 -> "HTTP/2.0";
            default -> "HTTP/1.1";
        };
    }

    private Har.Response toResponse(final HttpResponse<?> response) {
        final var allHeaders = toHeaders(response.headers());
        var content = new Body(-1, null);
        if (response.body() instanceof CharSequence strings) {
            content = extractBody(response, strings.toString().getBytes(StandardCharsets.UTF_8));
        } else if (response.body() instanceof byte[] bytes) {
            content = extractBody(response, bytes);
        } else if (response.body() instanceof Path path) {
            try {
                content = extractBody(response, Files.readAllBytes(path));
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        } else if (response.body() != null) {
            throw new IllegalArgumentException("Unsupported HAR support for body: " + response.body());
        }
        return new Har.Response(
                response.statusCode(),
                "",
                toHttpVersion(response.version()),
                null,
                allHeaders,
                content.content(),
                response.headers() == null ? null : response.headers().firstValue("Location").orElse(null),
                toHeaderSize(allHeaders),
                content.size(),
                "");
    }

    private long toHeaderSize(final Collection<Har.Header> headers) {
        return headers.stream()
                .mapToLong(h -> h.name.getBytes(StandardCharsets.UTF_8).length + h.value.getBytes(StandardCharsets.UTF_8).length + ": \r\n".length())
                .sum();
    }

    private Collection<Har.Header> toHeaders(final HttpHeaders headers) {
        return headers == null ? List.of() : headers.map().entrySet().stream()
                .map(it -> new Har.Header(it.getKey(), String.join(",", it.getValue()), ""))
                .collect(toList());
    }

    private Body extractBody(final HttpResponse<?> response, final byte[] body) {
        if (body == null) {
            return new Body(-1, null);
        }
        if (response.headers() != null) {
            return response.headers()
                    .firstValue("Content-Type")
                    .map(contentType -> new Body(
                            body.length,
                            List.of("application/octet-stream", "multipart/form-data").contains(contentType) ||
                                    contentType.startsWith("application/vnd.openxmlformats-officedocument") ?
                                    new Har.Content(body.length, 0, contentType, Base64.getEncoder().encodeToString(body), "base64", "") :
                                    new Har.Content(body.length, 0, contentType, new String(body, StandardCharsets.UTF_8), null, "")))
                    .orElseGet(() -> new Body(body.length, null));
        }
        return new Body(body.length, null);
    }

    private byte[] readRequestPayload(final HttpRequest request) {
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
        private final Instant instant;
        private final byte[] requestPayload;

        protected Data(final Instant instant, final byte[] requestPayload) {
            this.instant = instant;
            this.requestPayload = requestPayload;
        }
    }

    // see http://www.softwareishard.com/blog/har-12-spec/
    // mapping taken from https://github.com/rmannibucau/mock-server-generator
    // dates in "yyyy-MM-dd'T'HH:mm:ss.SSSX" format
    @JsonModel
    public record Har(Log log) {
        @JsonModel
        public record Log(String version, Identity creator, Identity browser, Collection<Page> pages,
                          Collection<Entry> entries, String comment) {
        }

        @JsonModel
        public record Request(
                String method,
                String url,
                String httpVersion,
                Collection<Cookie> cookies,
                Collection<Header> headers,
                Collection<Query> queryString,
                PostData postData,
                long headerSize,
                long bodySize,
                String comment) {
        }

        @JsonModel
        public record Response(
                int status,
                String statusText,
                String httpVersion,
                Collection<Cookie> cookies,
                Collection<Header> headers,
                Content content,
                String redirectURL,
                long headersSize,
                long bodySize,
                String comment) {
        }

        @JsonModel
        public record Query(String name, String value, String comment) {
        }

        @JsonModel
        public record PostData(String mimeType, Collection<Param> params, String text, String comment) {
        }

        @JsonModel
        public record Param(String name, String value, String fileName, String contentType, String comment) {
        }

        @JsonModel
        public record Cache(CacheRequest beforeRequest, CacheRequest afterRequest, String comment) {
        }

        @JsonModel
        public record CacheRequest(ZonedDateTime expires, ZonedDateTime lastAccess, String eTag, int hitCount,
                                   String comment) {
        }

        @JsonModel
        public record Timings(
                long blocked,
                long dns,
                long connect,
                long send,
                @Property("wait") long waitValue,
                long receive,
                long ssl,
                String comment) {
        }

        @JsonModel
        public record Cookie(
                ZonedDateTime expires,
                String name,
                String value,
                String path,
                String domain,
                boolean httpOnly,
                boolean secure,
                String comment) {
        }

        @JsonModel
        public record Header(String name, String value, String comment) {
        }

        @JsonModel
        public record Content(
                long size,
                int compression,
                String mimeType,
                String text,
                String encoding, // base64 if text is encoded
                String comment) {
        }

        @JsonModel
        public record Entry(
                ZonedDateTime startedDateTime, // yyyy-MM-dd'T'HH:mm:ss.SSSX
                String pageref,
                Long time,
                Request request,
                Response response,
                Cache cache,
                Timings timings,
                String serverIPAddress,
                String connection,
                String comment) {
        }

        @JsonModel
        public record Page(ZonedDateTime startedDateTime, String id, String title, PageTiming pageTimings,
                           String comment) {
        }

        @JsonModel
        public record PageTiming(long onContentLoad, long onLoad, String comment) {
        }

        @JsonModel
        public record Identity(String name, String version, String comment) {
        }
    }

    public static class BaseConfiguration<T extends BaseConfiguration<?>> {
        protected final Path output;
        protected final Clock clock;
        protected final Logger logger;

        private boolean enableStartedDateTime = true;
        private boolean enableTime = true;

        protected BaseConfiguration(final Path output, final Clock clock, final Logger logger) {
            this.output = output;
            this.clock = clock;
            this.logger = logger;
        }

        public boolean isEnableStartedDateTime() {
            return enableStartedDateTime;
        }

        public T setEnableStartedDateTime(boolean enableStartedDateTime) {
            this.enableStartedDateTime = enableStartedDateTime;
            return (T) this;
        }

        public boolean isEnableTime() {
            return enableTime;
        }

        public T setEnableTime(boolean enableTime) {
            this.enableTime = enableTime;
            return (T) this;
        }
    }

    private record Body(int size, Har.Content content) {
    }
}
