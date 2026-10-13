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
package io.yupiik.fusion.httpserver.jre;

import com.sun.net.httpserver.HttpExchange;
import io.yupiik.fusion.http.server.api.Body;
import io.yupiik.fusion.http.server.api.Cookie;
import io.yupiik.fusion.http.server.api.Request;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.stream.Stream;

/**
 * {@link Request} view over a JDK {@link HttpExchange}. It is cheap to create
 * and fully non-blocking beyond the underlying exchange stream reads the caller
 * performs. The body is exposed as a {@link Body} streaming the request
 * input stream (single subscription, like the servlet adapter).
 */
public class JreRequest implements Request {
    private final HttpExchange exchange;
    private final String path;
    private final String query;
    private final Map<String, String[]> parameters;
    private final Map<String, List<String>> headers;
    private final Map<String, Object> attributes = new HashMap<>();
    private final Body body;

    public JreRequest(final HttpExchange exchange) {
        this.exchange = exchange;
        this.path = exchange.getRequestURI().getPath();
        this.query = exchange.getRequestURI().getRawQuery();
        this.parameters = parseQuery(this.query);
        this.headers = parseHeaders(exchange.getRequestHeaders());
        this.body = new JreBody(exchange.getRequestBody());
    }

    @Override
    public String scheme() {
        return "http"; // the JRE server never terminates TLS itself
    }

    @Override
    public String method() {
        return exchange.getRequestMethod();
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String query() {
        return query;
    }

    @Override
    public Body fullBody() {
        return body;
    }

    @Override
    public Stream<Cookie> cookies() {
        final var values = headers.get("cookie");
        if (values == null || values.isEmpty()) {
            return Stream.empty();
        }
        final var cookies = new ArrayList<Cookie>();
        for (final var value : values) {
            final var parts = value.split(";");
            for (final var part : parts) {
                final var idx = part.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                final var name = part.substring(0, idx).strip();
                final var val = part.substring(idx + 1).strip();
                cookies.add(new SimpleCookie(name, val));
            }
        }
        return cookies.stream();
    }

    @Override
    public String parameter(final String name) {
        final var values = parameters.get(name);
        return values == null || values.length == 0 ? null : values[0];
    }

    @Override
    public Map<String, String[]> parameters() {
        return parameters;
    }

    @Override
    public String header(final String name) {
        final var values = headers.get(name.toLowerCase(Locale.ROOT));
        return values == null || values.isEmpty() ? null : String.join(", ", values);
    }

    @Override
    public Map<String, List<String>> headers() {
        return headers;
    }

    @Override
    public <T> T attribute(final String key, final Class<T> type) {
        final var value = attributes.get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    @Override
    public <T> void setAttribute(final String key, final T value) {
        attributes.put(key, value);
    }

    @Override
    public <T> T unwrapOrNull(final Class<T> type) {
        if (type == HttpExchange.class) {
            return type.cast(exchange);
        }
        if (type == InputStream.class) {
            return type.cast(exchange.getRequestBody());
        }
        if (type == Reader.class) {
            final var contentType = header("content-type");
            final var charset = charsetOf(contentType);
            return type.cast(new BufferedReader(new InputStreamReader(exchange.getRequestBody(), charset)));
        }
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        return null;
    }

    private static Charset charsetOf(final String contentType) {
        if (contentType != null) {
            final var idx = contentType.toLowerCase(Locale.ROOT).indexOf("charset=");
            if (idx >= 0) {
                final var value =
                        contentType.substring(idx + "charset=".length()).trim();
                if (!value.isEmpty()) {
                    try {
                        return Charset.forName(value.trim());
                    } catch (final IllegalArgumentException ignored) {
                        // fall through to UTF-8
                    }
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static Map<String, String[]> parseQuery(final String rawQuery) {
        final var out = new LinkedHashMap<String, String[]>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return out;
        }
        for (final var pair : rawQuery.split("&")) {
            final var eq = pair.indexOf('=');
            final var key = eq < 0 ? pair : pair.substring(0, eq);
            final var value = eq < 0 ? "" : pair.substring(eq + 1);
            out.compute(key, (_1, existing) -> {
                final var next = new String[(existing == null ? 0 : existing.length) + 1];
                if (existing != null) {
                    System.arraycopy(existing, 0, next, 0, existing.length);
                }
                next[next.length - 1] = value;
                return next;
            });
        }
        return out;
    }

    private static Map<String, List<String>> parseHeaders(final Map<String, List<String>> source) {
        final var out = new LinkedHashMap<String, List<String>>();
        source.forEach((key, values) -> out.put(key.toLowerCase(Locale.ROOT), List.copyOf(values)));
        return out;
    }

    /** Minimal read-only cookie compatible without the servlet-based {@code Cookie.of()} factory. */
    private record SimpleCookie(
            String name, String value, String path, String domain, int maxAge, boolean secure, boolean httpOnly)
            implements Cookie {

        private SimpleCookie(final String name, final String value) {
            this(name, value, null, null, -1, false, false);
        }
    }

    /** {@link Body} streaming the exchange input stream, single-subscription like {@code ServletBody}. */
    private static final class JreBody implements Body {
        private final InputStream stream;
        private volatile boolean subscribed;

        private JreBody(final InputStream stream) {
            this.stream = stream;
        }

        @Override
        public void subscribe(final Flow.Subscriber<? super ByteBuffer> subscriber) {
            if (subscribed) {
                subscriber.onError(new IllegalStateException("Already subscribed"));
                return;
            }
            subscribed = true;
            subscriber.onSubscribe(new Flow.Subscription() {
                private boolean done;

                @Override
                public void request(final long n) {
                    if (done) {
                        return;
                    }
                    done = true;
                    try {
                        final var bytes = stream.readAllBytes();
                        final var buffer = ByteBuffer.wrap(bytes);
                        subscriber.onNext(buffer);
                        subscriber.onComplete();
                    } catch (final Exception e) {
                        subscriber.onError(e);
                    }
                }

                @Override
                public void cancel() {
                    done = true;
                }
            });
        }

        @Override
        public Body cached() {
            return this;
        }

        @Override
        public CompletionStage<String> string() {
            return bytes().thenApply(b -> new String(b, StandardCharsets.UTF_8));
        }

        @Override
        public CompletionStage<byte[]> bytes() {
            final var future = new CompletableFuture<byte[]>();
            subscribe(new Flow.Subscriber<>() {
                private final List<ByteBuffer> buffers = new ArrayList<>();

                @Override
                public void onSubscribe(final Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(final ByteBuffer item) {
                    buffers.add(item);
                }

                @Override
                public void onError(final Throwable throwable) {
                    future.completeExceptionally(throwable);
                }

                @Override
                public void onComplete() {
                    final var total =
                            buffers.stream().mapToInt(ByteBuffer::remaining).sum();
                    final var out = new byte[total];
                    var offset = 0;
                    for (final var b : buffers) {
                        final var n = b.remaining();
                        b.get(out, offset, n);
                        offset += n;
                    }
                    future.complete(out);
                }
            });
            return future;
        }

        @Override
        public String parameter(final String name) {
            return null; // form encoding is not interpreted here
        }
    }
}
