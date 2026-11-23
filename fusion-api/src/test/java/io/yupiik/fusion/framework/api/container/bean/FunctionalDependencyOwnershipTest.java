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
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FunctionalDependencyOwnershipTest {
    @Test
    void missingOperationsAreNotInvokedAndDependentsStillBelongToTheInstance() {
        for (final var scope : List.of(DefaultScoped.class, ApplicationScoped.class)) {
            final var events = new ArrayList<String>();
            final java.util.function.BiFunction<io.yupiik.fusion.framework.api.RuntimeContainer, List<Instance<?>>, Object> constructor = (container, dependents) -> {
                dependents.add(new Dependency(events, "first"));
                dependents.add(new Dependency(events, "second"));
                return new Object();
            };
            final var bean = new FunctionalBean<>(Object.class, scope, 17, -3, Map.of("key", "value"), constructor, null, null, null, false);
            assertSame(Object.class, bean.type());
            assertEquals(17, bean.priority());
            assertEquals(-3, bean.destroyPriority());
            assertEquals(Map.of("key", "value"), bean.data());
            assertNotSame(bean.getLock(), new FunctionalBean<>(Object.class, scope, 17, -3, Map.of(), constructor, null, null, null, false).getLock());
            try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true).register(bean).start()) {
                try (final var instance = container.lookup(Object.class)) {
                    bean.inject(container, new ArrayList<>(), instance.instance());
                }
                assertEquals(scope == DefaultScoped.class ? List.of("second", "first") : List.of(), events);
            }
            assertEquals(List.of("second", "first"), events);
        }
    }

    private record Dependency(List<String> events, String name) implements Instance<String> {
        @Override
        public FusionBean<String> bean() { return null; }
        @Override
        public String instance() { return name; }
        @Override
        public void close() { events.add(name); }
    }
}
