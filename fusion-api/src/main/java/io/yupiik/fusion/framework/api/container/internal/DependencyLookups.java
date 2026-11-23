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

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;

import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

/** Dependency lookup and ownership shared by generated callbacks. */
public final class DependencyLookups {
    private DependencyLookups() {
        // no-op
    }

    public static Object lookup(final RuntimeContainer container, final Type type, final List<Instance<?>> dependents) {
        final var instance = container.lookup(type);
        dependents.add(instance);
        return instance.instance();
    }

    public static <T> T lookup(final RuntimeContainer container, final Class<T> type, final List<Instance<?>> dependents) {
        return type.cast(lookup(container, (Type) type, dependents));
    }

    public static <A, T> T lookups(final RuntimeContainer container, final Class<A> type,
                                 final Function<List<Instance<A>>, T> postProcessor,
                                 final List<Instance<?>> dependents) {
        final var instance = container.lookups(type, postProcessor);
        dependents.add(instance);
        return instance.instance();
    }

    public static <T> List<T> orderedList(final RuntimeContainer container, final Class<T> type,
                                        final boolean comparable, final List<Instance<?>> dependents) {
        final var instance = list(container, type, comparable);
        dependents.add(instance);
        return instance.instance();
    }

    public static <T> Instance<List<T>> list(final RuntimeContainer container, final Class<T> type, final boolean comparable) {
        return container.lookups(type, new ListValues<>(comparable));
    }

    public static <T> Instance<Set<T>> set(final RuntimeContainer container, final Class<T> type) {
        return container.lookups(type, new SetValues<>());
    }

    public static <T> Set<T> set(final RuntimeContainer container, final Class<T> type, final List<Instance<?>> dependents) {
        final var instance = set(container, type);
        dependents.add(instance);
        return instance.instance();
    }

    private record ListValues<T>(boolean comparable) implements Function<List<Instance<T>>, List<T>> {
        @Override
        public List<T> apply(final List<Instance<T>> instances) {
            return comparable ? instances.stream().map(Instance::instance).sorted().collect(toList()) :
                    instances.stream().sorted(Comparator.comparing(it -> it.bean().priority())).map(Instance::instance).collect(toList());
        }
    }

    private static final class SetValues<T> implements Function<List<Instance<T>>, Set<T>> {
        @Override
        public Set<T> apply(final List<Instance<T>> instances) {
            final var values = new HashSet<T>();
            for (final var instance : instances) {
                values.add(instance.instance());
            }
            return values;
        }
    }
}
