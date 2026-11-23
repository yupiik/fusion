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
package io.yupiik.fusion.framework.api.container.internal;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.BaseLookup;
import io.yupiik.fusion.framework.api.container.bean.FunctionalBean;
import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.framework.api.exception.NoMatchingBeanException;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DependencyLookupsTest extends BaseLookup {
    private int lookupCalls;

    @Override
    protected <A, T> T lookups(final RuntimeContainer container,
                               final Class<A> type,
                               final Function<List<Instance<A>>, T> postProcessor,
                               final List<Instance<?>> dependents) {
        lookupCalls++;
        return super.lookups(container, type, postProcessor, dependents);
    }

    @Test
    void legacyProtectedHelpersRetainOwnershipAndOrdering() {
        final var events = new ArrayList<String>();
        try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true)
                .register(bean("z", 1, events), bean("a", 2, events)).start()) {
            final var dependents = new ArrayList<Instance<?>>();
            assertEquals("a", lookup(container, String.class, dependents));
            assertEquals("a", lookup(container, (Type) String.class, dependents));
            assertEquals(List.of("z", "a"), orderedList(container, String.class, false, dependents));
            assertEquals(List.of("a", "z"), orderedList(container, String.class, true, dependents));
            final int count = lookups(container, String.class, List::size, dependents);
            assertEquals(2, count);
            assertEquals(5, dependents.size());
            assertEquals(3, lookupCalls, "orderedList must retain the protected lookups extension point");
            assertEquals(List.of(), events);
            dependents.forEach(Instance::close);
            assertEquals(8, events.size());
        }
    }

    @Test
    void classAndParameterizedLookupsRetainTheirOwningHandles() {
        final var events = new ArrayList<String>();
        final var listType = new Types.ParameterizedTypeImpl(List.class, String.class);
        final var listBean = new FunctionalBean<>(listType, DefaultScoped.class, 1000, 0, Map.of(),
                (container, dependents) -> List.of("value"), null, null,
                (container, value) -> events.add("list"), false);
        try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true)
                .register(bean("value", 1000, events), listBean).start()) {
            final var dependents = new ArrayList<Instance<?>>();
            assertEquals("value", DependencyLookups.lookup(container, String.class, dependents));
            assertEquals(List.of("value"), DependencyLookups.lookup(container, listType, dependents));
            assertEquals(2, dependents.size());
            assertEquals(List.of(), events);
            dependents.get(1).close();
            dependents.get(0).close();
            assertEquals(List.of("list", "value"), events);
        }
    }

    @Test
    void collectionLookupsPreserveOrderingAndRetainAggregateHandles() {
        final var events = new ArrayList<String>();
        try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true)
                .register(bean("z", 1, events), bean("a", 2, events)).start()) {
            final var dependents = new ArrayList<Instance<?>>();
            assertEquals(List.of("z", "a"), DependencyLookups.orderedList(container, String.class, false, dependents));
            assertEquals(List.of("a", "z"), DependencyLookups.orderedList(container, String.class, true, dependents));
            assertEquals(Set.of("a", "z"), DependencyLookups.set(container, String.class, dependents));
            final int count = DependencyLookups.lookups(container, String.class, List::size, dependents);
            assertEquals(2, count);
            assertEquals(4, dependents.size());
            assertEquals(List.of(), events);
            dependents.forEach(Instance::close);
            assertEquals(8, events.size());
            assertEquals(4, events.stream().filter("a"::equals).count());
            assertEquals(4, events.stream().filter("z"::equals).count());
        }
    }

    @Test
    void failedLookupsDoNotAddAnOwningHandle() {
        try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true).start()) {
            final var dependents = new ArrayList<Instance<?>>();
            assertThrows(NoMatchingBeanException.class,
                    () -> DependencyLookups.lookup(container, String.class, dependents));
            assertEquals(List.of(), dependents);
        }
    }

    private FunctionalBean<String> bean(final String value, final int priority, final List<String> events) {
        return new FunctionalBean<>(String.class, DefaultScoped.class, priority, 0, Map.of(),
                (container, dependents) -> value, null, null,
                (container, instance) -> events.add(instance), false);
    }
}
