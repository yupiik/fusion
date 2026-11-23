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
package io.yupiik.fusion.framework.api.container.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.listener.FunctionalListener;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FunctionalCallbacksTest {
    @Test
    void callbackSharingDoesNotShareBeanIdentityOrLocks() {
        final var value = new Object();
        final BiFunction<RuntimeContainer, List<Instance<?>>, Object> constructor =
                (container, dependents) -> value;
        final var first = new FunctionalBean<>(Object.class, DefaultScoped.class, 3, 7, Map.of("key", "value"),
                constructor, null, null, null, false);
        final var second = new FunctionalBean<>(Object.class, DefaultScoped.class, 3, 7, Map.of("key", "value"),
                constructor, null, null, null, false);
        assertNotEquals(first, second);
        assertNotSame(first.getLock(), second.getLock());
        assertNotSame(first, second);
        assertEquals(3, first.priority());
        assertEquals(7, first.destroyPriority());
        assertSame(Object.class, first.type());
        assertSame(DefaultScoped.class, first.scope());
        assertEquals(Map.of("key", "value"), first.data());
        final var dependencies = new ArrayList<Instance<?>>();
        assertSame(value, first.create(null, dependencies));
        first.inject(null, dependencies, value);
        first.destroy(null, value);
        assertEquals(List.of(), dependencies);
    }

    @Test
    void explicitDestructionWinsOverAutoCloseAndFailuresAreNotRedispatched() {
        final var trace = new ArrayList<String>();
        final AutoCloseable product = () -> trace.add("close");
        final var failure = new IllegalArgumentException("destroy");
        final var bean = new FunctionalBean<>(AutoCloseable.class, DefaultScoped.class, 0, 0, Map.of(),
                (container, dependencies) -> product, null, null, (container, instance) -> {
                    trace.add("destroy");
                    throw failure;
                }, true);
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> bean.destroy(null, product)));
        assertEquals(List.of("destroy"), trace);
        final var closeFailure = new IOException("close");
        final AutoCloseable failingProduct = () -> { throw closeFailure; };
        final var autoClose = new FunctionalBean<>(AutoCloseable.class, DefaultScoped.class, 0, 0, Map.of(),
                (container, dependencies) -> failingProduct, null, null, null, true);
        assertSame(closeFailure, assertThrows(IllegalStateException.class, () -> autoClose.destroy(null, failingProduct)).getCause());
    }

    @Test
    void creationStopsAtFailingInjectionAndExplicitInjectionNeverInitializes() {
        final var trace = new ArrayList<String>();
        final var failure = new IllegalStateException("inject");
        final var bean = new FunctionalBean<>(Object.class, DefaultScoped.class, 0, 0, Map.of(),
                (container, dependencies) -> { trace.add("construct"); return new Object(); },
                (container, dependencies, instance) -> { trace.add("inject"); throw failure; },
                (container, instance) -> trace.add("init"), null, false);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> bean.create(null, new ArrayList<>())));
        assertEquals(List.of("construct", "inject"), trace);
        trace.clear();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> bean.inject(null, new ArrayList<>(), new Object())));
        assertEquals(List.of("inject"), trace);
    }

    @Test
    void listenersKeepEventTypePriorityAndOriginalException() {
        final var failure = new IllegalArgumentException("event");
        final var listener = new FunctionalListener<>(String.class, 19, (container, event) -> {
            assertEquals("event", event);
            throw failure;
        });
        assertSame(String.class, listener.eventType());
        assertEquals(19, listener.priority());
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> listener.onEvent(null, "event")));
    }
}
