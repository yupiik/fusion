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
package io.yupiik.fusion.jsonrpc.impl;

import io.yupiik.fusion.http.server.api.Body;
import io.yupiik.fusion.http.server.api.Cookie;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.jsonrpc.JsonRpcException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonRpcParametersTest {
    @Test
    void descriptorsShareImplementationAndRetainMetadataAndAsyncTiming() {
        final var target = new Target(new CompletableFuture<>(), new ArrayList<>());
        final var first = new DefaultJsonRpcMethod(12, "convert", Callbacks::convert, false, Map.of("tag", "one"));
        final var second = new DefaultJsonRpcMethod(34, "notify", context -> Callbacks.notify(target, context), true, Map.of("tag", "two"));
        assertEquals(first.getClass(), second.getClass());
        assertEquals("convert", first.name());
        assertEquals("notify", second.name());
        assertEquals(12, first.priority());
        assertEquals(34, second.priority());
        assertFalse(first.isNotification());
        assertTrue(second.isNotification());
        assertEquals(Map.of("tag", "one"), first.metadata());
        assertEquals(Map.of("tag", "two"), second.metadata());

        final var request = new TestRequest();
        final var named = new JsonRpcMethod.Context(request, Map.of("count", new BigDecimal("42"), "names", List.of("a", "b")));
        final var positional = new JsonRpcMethod.Context(request, List.of(new BigDecimal("17"), List.of("c")));
        assertEquals(new Converted(request, 42, List.of("a", "b")), first.invoke(named).toCompletableFuture().join());
        assertEquals(new Converted(request, 17, List.of("c")), first.invoke(positional).toCompletableFuture().join());
        assertSame(target.async(), second.invoke(positional));
        assertFalse(target.async().isDone());
        target.async().complete("done");
        assertEquals("done", second.invoke(named).toCompletableFuture().join());
        assertEquals(List.of(positional, named), target.notifications());
    }

    @Test
    void lookupAndValidationKeepProtocolErrorsAndNullSemantics() {
        final var absent = new JsonRpcMethod.Context(null, null);
        assertNull(JsonRpcParameters.findParameter(absent, "x", 0, false));
        assertNull(JsonRpcParameters.findParameter(new JsonRpcMethod.Context(null, List.of()), "x", 1, false));
        final var missing = assertThrows(JsonRpcException.class,
                () -> JsonRpcParameters.findParameter(absent, "x", 0, true));
        assertEquals(-32601, missing.code());
        assertEquals("Missing 'x' parameter.", missing.getMessage());
        final var invalid = assertThrows(JsonRpcException.class,
                () -> JsonRpcParameters.findParameter(new JsonRpcMethod.Context(null, "bad"), "x", 0, false));
        assertEquals(-32700, invalid.code());
        assertEquals("Invalid parameter instance, expected an array or an object but got 'String'", invalid.getMessage());
        final var invalidCollection = assertThrows(JsonRpcException.class,
                () -> JsonRpcParameters.failIfNotCollection("xs", "bad"));
        assertEquals(-32700, invalidCollection.code());
        assertEquals("Invalid parameter 'xs', got a String", invalidCollection.getMessage());
        final var invalidMap = assertThrows(JsonRpcException.class,
                () -> JsonRpcParameters.failIfNotMap("xs", List.of()));
        assertEquals(-32700, invalidMap.code());
        final var map = Map.<String, Object>of("a", 1);
        assertSame(map, JsonRpcParameters.failIfNotMap("xs", map));
    }

    @Test
    void synchronousErrorsAreNotTurnedIntoFailedStages() {
        final var target = new Target(new CompletableFuture<>(), new ArrayList<>());
        final var method = new DefaultJsonRpcMethod(0, "required", Callbacks::convert);
        final var error = assertThrows(JsonRpcException.class, () -> method.invoke(new JsonRpcMethod.Context(null, List.of())));
        assertEquals(-32601, error.code());
        final var async = new DefaultJsonRpcMethod(0, "async", context -> Callbacks.notify(target, context));
        final var stage = async.invoke(new JsonRpcMethod.Context(null, null));
        final var failure = new IllegalStateException("async failure");
        target.async().completeExceptionally(failure);
        assertSame(failure, assertThrows(java.util.concurrent.CompletionException.class,
                () -> stage.toCompletableFuture().join()).getCause());
    }
    @Test
    void legacyProtectedLookupRetainsTheSameContract() {
        for (final var params : List.of(Map.of("x", 12), List.of(12))) {
            final var context = new JsonRpcMethod.Context(null, params);
            assertEquals(JsonRpcParameters.findParameter(context, "x", 0, true), LegacyMethod.lookup(context, true));
        }
        final var absent = new JsonRpcMethod.Context(null, null);
        assertNull(LegacyMethod.lookup(absent, false));
        final var missing = assertThrows(JsonRpcException.class, () -> LegacyMethod.lookup(absent, true));
        assertEquals(-32601, missing.code());
        assertEquals("Missing 'x' parameter.", missing.getMessage());
        final var invalid = assertThrows(JsonRpcException.class,
                () -> LegacyMethod.lookup(new JsonRpcMethod.Context(null, "bad"), false));
        assertEquals(-32700, invalid.code());
    }

    @Test
    void legacyProtectedValidationRetainsTheSameContract() {
        final var map = Map.<String, Object>of("x", 12);
        assertSame(map, LegacyMethod.validateMap(map));
        assertSame(map, LegacyMethod.validate(map));
        final var invalidMap = assertThrows(JsonRpcException.class, () -> LegacyMethod.validateMap("bad"));
        assertEquals(-32700, invalidMap.code());
        assertEquals("Invalid parameter 'x', got a String", invalidMap.getMessage());
        final var invalid = assertThrows(JsonRpcException.class, () -> LegacyMethod.validate("bad"));
        assertEquals(invalidMap.code(), invalid.code());
        assertEquals(invalidMap.getMessage(), invalid.getMessage());
    }

    private static final class LegacyMethod extends DefaultJsonRpcMethod {
        private LegacyMethod() {
            super(1000, "legacy", null);
        }

        private static Object lookup(final JsonRpcMethod.Context context, final boolean required) {
            return findParameter(context, "x", 0, required, Function.identity());
        }

        private static Map<String, Object> validateMap(final Object value) {
            return failIfNotMap("x", value);
        }

        private static Object validate(final Object value) {
            return failIfNot("x", value, it -> it instanceof Map<?, ?>);
        }
    }


    private record Target(CompletableFuture<Object> async, List<JsonRpcMethod.Context> notifications) {
    }

    private record Converted(Request request, int count, List<String> names) {
    }

    private static final class Callbacks {
        private static CompletionStage<Converted> convert(final JsonRpcMethod.Context context) {
            // Request occupies no positional parameter slot. Conversions stay statically typed, with no mapper lambda.
            final var count = ((Number) JsonRpcParameters.findParameter(context, "count", 0, true)).intValue();
            final var rawNames = JsonRpcParameters.findParameter(context, "names", 1, false);
            final List<String> names;
            if (rawNames == null) {
                names = null;
            } else {
                names = new ArrayList<>();
                for (final var name : JsonRpcParameters.failIfNotCollection("names", rawNames)) {
                    names.add((String) name);
                }
            }
            return CompletableFuture.completedFuture(new Converted(context.request(), count, names));
        }

        private static CompletionStage<?> notify(final Target target, final JsonRpcMethod.Context context) {
            target.notifications().add(context);
            return target.async();
        }
    }

    private static final class TestRequest implements Request {
        @Override
        public String scheme() { return "http"; }
        @Override
        public String method() { return "POST"; }
        @Override
        public String path() { return "/jsonrpc"; }
        @Override
        public String query() { return null; }
        @Override
        public Body fullBody() { return null; }
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
        public <T> void setAttribute(final String key, final T value) { throw new UnsupportedOperationException(); }
    }
}
