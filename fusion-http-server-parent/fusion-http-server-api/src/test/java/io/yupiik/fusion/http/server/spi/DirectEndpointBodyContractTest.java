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
package io.yupiik.fusion.http.server.spi;

import io.yupiik.fusion.http.server.api.Body;
import io.yupiik.fusion.http.server.api.Cookie;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.impl.flow.BytesPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DirectEndpointBodyContractTest {
    @Test
    void multipleBodiesAreRejectedBeforeAnyRequestOrCallback() {
        assertEquals("HTTP endpoints support at most one JSON body parameter.",
                assertThrows(IllegalArgumentException.class, () -> new DirectEndpointHandler(
                        new Type[]{String.class, Integer.class}, null, null, null)).getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"stream", "reader", "publisher"})
    void callerTypeArrayIsCopiedAndBodyIsConsumedOnce(final String mode) {
        final var types = new Type[]{String.class};
        final var reads = new AtomicInteger();
        final var response = Response.of().status(201).build();
        final var request = new TestRequest(mode);
        final var handler = new DirectEndpointHandler(types, (req, body) -> {
            assertSame(request, req);
            assertEquals(1, body.length);
            assertEquals("decoded", body[0]);
            return CompletableFuture.completedStage(response);
        }, (type, body) -> {
            reads.incrementAndGet();
            assertSame(String.class, type);
            if (body instanceof char[] chars) {
                assertEquals("payload", new String(chars));
            } else {
                assertSame(request.expectedBody(), body);
            }
            return "decoded";
        }, null);
        types[0] = Integer.class;
        assertSame(response, handler.apply(request).toCompletableFuture().join());
        assertEquals(1, reads.get());
        assertEquals("publisher".equals(mode) ? 1 : 0, request.subscriptions.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"stream", "reader", "publisher"})
    void decoderFailuresKeepTheirTiming(final String mode) {
        final var error = new IllegalStateException("decode");
        final var handler = new DirectEndpointHandler(new Type[]{String.class}, (req, body) -> {
            throw new AssertionError("Must not invoke after decoding failure");
        }, (type, body) -> { throw error; }, null);
        final var request = new TestRequest(mode);
        if ("publisher".equals(mode)) {
            final var result = handler.apply(request);
            assertSame(error, assertThrows(CompletionException.class, () -> result.toCompletableFuture().join()).getCause());
        } else {
            assertSame(error, assertThrows(IllegalStateException.class, () -> handler.apply(request)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"stream", "reader", "publisher"})
    void businessFailureAfterBodyIsAlwaysAsynchronous(final String mode) {
        final var error = new IllegalStateException("business");
        final var handler = new DirectEndpointHandler(new Type[]{String.class}, (req, body) -> {
            throw error;
        }, (type, body) -> "decoded", null);
        final var result = handler.apply(new TestRequest(mode));
        assertSame(error, assertThrows(CompletionException.class, () -> result.toCompletableFuture().join()).getCause());
    }

    private static final class TestRequest implements Request {
        private final String mode;
        private final InputStream stream = new ByteArrayInputStream(new byte[0]);
        private final Reader reader = new StringReader("");
        private final AtomicInteger subscriptions = new AtomicInteger();

        private TestRequest(final String mode) { this.mode = mode; }
        private Object expectedBody() { return "stream".equals(mode) ? stream : reader; }
        @Override
        public <T> T unwrapOrNull(final Class<T> type) {
            if (type == InputStream.class && "stream".equals(mode)) {
                return type.cast(stream);
            }
            if (type == Reader.class) {
                assertFalse("stream".equals(mode), "InputStream must take precedence over Reader");
                return "reader".equals(mode) ? type.cast(reader) : null;
            }
            return null;
        }
        @Override
        public Body fullBody() {
            assertEquals("publisher", mode, "Unwrapped bodies must not subscribe to the publisher");
            return new Body() {
                @Override
                public Body cached() { throw new AssertionError("No replay required"); }
                @Override
                public CompletionStage<String> string() { throw new AssertionError(); }
                @Override
                public CompletionStage<byte[]> bytes() { throw new AssertionError(); }
                @Override
                public String parameter(final String name) { return null; }
                @Override
                public void subscribe(final Flow.Subscriber<? super ByteBuffer> subscriber) {
                    assertEquals(1, subscriptions.incrementAndGet(), "Single-use request body");
                    new BytesPublisher("payload".getBytes(StandardCharsets.UTF_8)).subscribe(subscriber);
                }
            };
        }
        @Override
        public String scheme() { return "http"; }
        @Override
        public String method() { return "POST"; }
        @Override
        public String path() { return "/"; }
        @Override
        public String query() { return null; }
        @Override
        public Stream<Cookie> cookies() { return Stream.empty(); }
        @Override
        public String parameter(final String name) { return null; }
        @Override
        public Map<String, String[]> parameters() { return Map.of(); }
        @Override
        public String header(final String name) { return null; }
        @Override
        public Map<String, List<String>> headers() { return Map.of(); }
        @Override
        public <T> T attribute(final String key, final Class<T> type) { return null; }
        @Override
        public <T> void setAttribute(final String key, final T value) { }
    }
}
