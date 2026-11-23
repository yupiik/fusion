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

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.framework.api.container.listener.FunctionalListener;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class FunctionalLifecycleTest {
    @Test
    void identityLocksAndRegistrationTypeAreNotAdapterMetadataEquality() {
        final var table = new Table();
        final var type = new Types.ParameterizedTypeImpl(List.class, String.class);
        final var first = new FunctionalBean<List<String>>(type, DefaultScoped.class, 42, -7, Map.of("key", "value"), (container, dependents) -> List.of(), null, null, null, false);
        final var second = new FunctionalBean<List<String>>(type, DefaultScoped.class, 42, -7, Map.of("key", "value"), (container, dependents) -> List.of(), null, null, null, false);
        assertSame(type, first.type());
        assertEquals(DefaultScoped.class, first.scope());
        assertEquals(42, first.priority());
        assertEquals(-7, first.destroyPriority());
        assertEquals(Map.of("key", "value"), first.data());
        assertNotSame(first, second);
        assertNotEquals(first, second);
        assertSame(first.getLock(), first.getLock());
        assertNotSame(first.getLock(), second.getLock());
    }

    @Test
    void defaultLifecycleKeepsDependentReverseClose() {
        final var table = new Table();
        final var bean = new FunctionalBean<Token>(Token.class, DefaultScoped.class, 1000, 0, Map.of(), table::create, table::inject, null, table::destroy, false);
        try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true).register(bean).start()) {
            try (final var first = container.lookup(Token.class);
                 final var second = container.lookup(Token.class)) {
                assertNotSame(first.instance(), second.instance());
                assertSame(bean, first.bean());
                assertEquals(List.of("create", "inject", "create", "inject"), table.events);
            }
            assertEquals(List.of("create", "inject", "create", "inject", "destroy", "second", "first", "destroy", "second", "first"), table.events);
        }
    }

    @Test
    void applicationLifecycleAndListenerForwardingUseIndependentAdapters() {
        final var table = new Table();
        final var bean = new FunctionalBean<Token>(Token.class, ApplicationScoped.class, 1000, 0, Map.of(), table::create, table::inject, null, table::destroy, false);
        final var listener = new FunctionalListener<String>(String.class, 27, (container, event) -> table.events.add("9:" + event));
        assertEquals(27, listener.priority());
        assertEquals(String.class, listener.eventType());
        try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true).register(bean).register(listener).start()) {
            try (final var first = container.lookup(Token.class); final var second = container.lookup(Token.class)) {
                assertSame(first.instance(), second.instance());
            }
            container.emit("message");
            assertEquals(List.of("create", "inject", "9:message"), table.events);
        }
        assertEquals(List.of("create", "inject", "9:message", "destroy", "second", "first"), table.events);
    }

    private static final class Token {
        // identity only
    }

    private static final class Table {
        private final List<String> events = new ArrayList<>();

        private Token create(final RuntimeContainer container, final List<Instance<?>> dependents) {
            events.add("create");
            dependents.add(new Dependency(events, "first"));
            dependents.add(new Dependency(events, "second"));
            return new Token();
        }

        private void inject(final RuntimeContainer container, final List<Instance<?>> dependents, final Token value) {
            events.add("inject");
        }

        private void destroy(final RuntimeContainer container, final Token value) {
            events.add("destroy");
        }
    }

    private record Dependency(List<String> events, String name) implements Instance<String> {
        @Override
        public FusionBean<String> bean() {
            return null;
        }

        @Override
        public String instance() {
            return name;
        }

        @Override
        public void close() {
            events.add(name);
        }
    }
}
