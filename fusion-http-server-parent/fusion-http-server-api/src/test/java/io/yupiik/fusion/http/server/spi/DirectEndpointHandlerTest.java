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

import io.yupiik.fusion.http.server.api.Response;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectEndpointHandlerTest {
    @Test
    void sharedImplementationUsesDirectCallbacksWithoutAwaiting() {
        final var first = new State(new CompletableFuture<>(), true);
        final var second = new State(new CompletableFuture<>(), false);
        final var firstAdapter = new DirectEndpointHandler(new java.lang.reflect.Type[0], (request, body) -> first.result(), null, null);
        final var secondAdapter = new DirectEndpointHandler(new java.lang.reflect.Type[0], (request, body) -> second.result(), null, null);
        final var a = Endpoint.of(request -> first.matches(), firstAdapter, 10);
        final var b = Endpoint.of(request -> !second.matches(), secondAdapter, 20);
        assertEquals(a.getClass(), b.getClass());
        assertEquals(10, a.priority());
        assertEquals(20, b.priority());
        assertTrue(a.matches(null));
        assertTrue(b.matches(null));
        assertSame(first.result(), a.handle(null));
        assertSame(second.result(), b.handle(null));
        assertFalse(first.result().isDone());
        first.result().complete(null);
        assertTrue(a.handle(null).toCompletableFuture().isDone());
        assertFalse(second.result().isDone());
    }

    @Test
    void synchronousAndAsynchronousFailuresKeepTheirTiming() {
        final var error = new IllegalStateException("broken");
        final var target = new State(new CompletableFuture<>(), false);
        final var adapter = new DirectEndpointHandler(new java.lang.reflect.Type[0], (request, body) -> { throw error; }, null, null);
        assertSame(error, assertThrows(IllegalStateException.class, () -> adapter.apply(null)));
        final var async = new DirectEndpointHandler(new java.lang.reflect.Type[0], (request, body) -> target.result(), null, null);
        final var stage = async.apply(null);
        assertFalse(stage.toCompletableFuture().isDone());
        target.result().completeExceptionally(error);
        assertSame(error, assertThrows(java.util.concurrent.CompletionException.class,
                () -> stage.toCompletableFuture().join()).getCause());
    }

    @Test
    void serializationRunsAfterCompletionAndItsErrorsFailTheResponseStage() {
        final var business = new CompletableFuture<String>();
        final var serialized = new java.util.ArrayList<Object>();
        final var error = new IllegalArgumentException("serialization");
        final var handler = new DirectEndpointHandler(new java.lang.reflect.Type[0], (request, body) -> business, null, value -> {
            serialized.add(value);
            throw error;
        });
        final var response = handler.apply(null).toCompletableFuture();
        assertTrue(serialized.isEmpty());
        assertFalse(response.isDone());
        business.complete("ready");
        assertEquals(java.util.List.of("ready"), serialized);
        assertSame(error, assertThrows(java.util.concurrent.CompletionException.class, response::join).getCause());
    }

    private record State(CompletableFuture<Response> result, boolean matches) {
    }

}
