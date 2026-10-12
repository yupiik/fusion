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
package io.yupiik.fusion.framework.api.container.context;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.DefaultInstance;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static java.util.stream.Collectors.toCollection;

public class DefaultFusionContext extends BaseContext {
    @Override
    public Class<?> marker() {
        return DefaultScoped.class;
    }

    @Override
    public <T> Instance<T> getOrCreate(final RuntimeContainer container, final FusionBean<T> bean) {
        final var dependents = new Dependents();
        final var instance = bean.create(container, dependents);
        Collections.reverse(dependents); // destroy in reverse order (LIFO) unless a destroy priority overrides it
        // only order when a prioritized dependency exists: the common (no priority) case keeps the reversed list
        // untouched, avoiding any extra iteration/allocations; DefaultInstance requires a mutable list so the
        // ordered stream is collected into an ArrayList
        final var ordered = dependents.anyPrioritized ?
                // Dependents being an ArrayList its stream() is order-stable so priority=0 are kept reversed as expected
                sortByDestroyOrder(dependents.stream()).collect(toCollection(ArrayList::new)) :
                dependents;
        return new DefaultInstance<>(bean, container, instance, ordered);
    }

    /**
     * Tracks whether a prioritized (destroyPriority != 0) dependency was added while the bean was created,
     * so the destroy ordering can be skipped when not needed without re-iterating the whole list.
     */
    private static final class Dependents extends ArrayList<Instance<?>> {
        private boolean anyPrioritized;

        @Override
        public boolean add(final Instance<?> instance) {
            if (!anyPrioritized && instance.bean() != null && instance.bean().destroyPriority() != 0) {
                anyPrioritized = true;
            }
            return super.add(instance);
        }

        @Override
        public boolean addAll(final Collection<? extends Instance<?>> c) {
            c.forEach(this::add);
            return !c.isEmpty();
        }
    }
}
