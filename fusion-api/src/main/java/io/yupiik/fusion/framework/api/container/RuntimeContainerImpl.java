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
package io.yupiik.fusion.framework.api.container;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.bean.NullBean;
import io.yupiik.fusion.framework.api.container.instance.OptionalInstance;
import io.yupiik.fusion.framework.api.exception.AmbiguousBeanException;
import io.yupiik.fusion.framework.api.exception.MissingContextException;
import io.yupiik.fusion.framework.api.exception.NoMatchingBeanException;
import io.yupiik.fusion.framework.api.lifecycle.Stop;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static java.util.Comparator.comparing;
import static java.util.Optional.empty;
import static java.util.stream.Collectors.joining;

public class RuntimeContainerImpl implements RuntimeContainer {
    private final Beans beans;
    private final Contexts contexts;
    private final Listeners listeners;
    private final Types types = newTypes();

    private final Map<Type, Type> slowLookupMatchings = new ConcurrentHashMap<>();
    private final Map<Type, List<FusionBean<?>>> listMatchings = new ConcurrentHashMap<>();

    public RuntimeContainerImpl(final Beans beans, final Contexts contexts, final Listeners listeners) {
        this.beans = beans;
        this.contexts = contexts;
        this.listeners = listeners;
    }

    @Override
    public Beans getBeans() {
        return beans;
    }

    @Override
    public Contexts getContexts() {
        return contexts;
    }

    @Override
    public Listeners getListeners() {
        return listeners;
    }

    @Override
    public <T> Instance<T> lookup(final Class<T> type) {
        return lookup((Type) type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Instance<T> lookup(final Type inType) {
        final Type type;
        final boolean optional;
        if (inType instanceof ParameterizedType pt && pt.getRawType() == Optional.class) {
            type = pt.getActualTypeArguments()[0];
            optional = true;
        } else {
            type = inType;
            optional = false;
        }

        var existing = beans.getBeans().get(type);
        if (existing == null) {
            final var found = slowLookupMatchings.get(type);
            if (found == null) { // slower but wider matching
                final var matching = beans.getBeans().entrySet().stream()
                        .filter(it -> types.isAssignable(it.getKey(), type))
                        .toList();
                if (matching.size() == 1) {
                    final var exactMatching = matching.get(0);
                    slowLookupMatchings.put(type, exactMatching.getKey());
                    final var lookup = lookup(exactMatching.getKey());
                    return wrapIfNeeded(optional, lookup);
                }
                if (matching.size() > 1) {
                    existing = matching.stream()
                            .map(Map.Entry::getValue)
                            .flatMap(Collection::stream)
                            .sorted(Comparator.<FusionBean<?>, Integer>comparing(FusionBean::priority).reversed())
                            .toList();
                    if (existing.get(1).priority() < existing.get(0).priority()) { // use first bean
                        final var exactMatching = existing.get(0);
                        slowLookupMatchings.put(type, exactMatching.type());
                        return wrapIfNeeded(optional, doGetInstance(optional, (FusionBean<T>) existing.get(0)));
                    }
                }
            } else {
                final var lookup = lookup(found);
                return wrapIfNeeded(optional, lookup);
            }

            if (optional) {
                return new DefaultInstance<>(new NullBean<>(type), this, (T) empty(), List.of());
            }
            throw new NoMatchingBeanException("No bean matching type '" + type.getTypeName() + "'");
        }

        if (existing.size() != 1) {
            if (existing.size() > 1) { // enable to override beans using priority (max wins)
                final int max = existing.stream().mapToInt(FusionBean::priority).max().orElse(Integer.MIN_VALUE);
                if (existing.get(1).priority() < max) { // use first bean
                    return doGetInstance(optional, (FusionBean<T>) existing.get(0));
                }
            }

            throw new AmbiguousBeanException("Matching beans for '" + type.getTypeName() + "': " +
                    existing.stream().map(FusionBean::type).map(Type::getTypeName).collect(joining(",", "", ".")));
        }

        return doGetInstance(optional, (FusionBean<T>) existing.get(0));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A, T> Instance<T> lookups(final Class<A> type,
                                      final Function<List<Instance<A>>, T> postProcessor) {
        // note: for now this does not go through scopes so ensure to inject it in a bean with the right scope
        //       to not pay a high lookup cost (app scope for ex)
        var matching = listMatchings.get(type);
        if (matching == null) {
            matching = beans.getBeans().entrySet().stream()
                    .filter(it -> types.isAssignable(it.getKey(), type))
                    .map(Map.Entry::getValue)
                    .flatMap(Collection::stream)
                    .sorted(Comparator.<FusionBean<?>, Integer>comparing(FusionBean::priority).thenComparing(it -> it.type().getTypeName()))
                    .toList();
            listMatchings.putIfAbsent(type, matching);
        }
        final var dependents = matching.stream()
                .map(it -> (Instance<A>) doGetInstance(false, it))
                .toList();
        final var instance = postProcessor.apply(dependents);
        return new DefaultInstance<>(null, this, instance, new ArrayList<>(dependents));
    }

    @Override
    public void close() {
        final var error = new IllegalStateException("Can't close the container properly");

        // stop event
        if (listeners.hasDirectListener(Stop.class)) {
            try {
                listeners.fire(this, new Stop());
            } catch (final RuntimeException re) {
                error.addSuppressed(re);
            }
        }

        // cleanup contexts
        try {
            contexts.close();
        } catch (final RuntimeException re) {
            error.addSuppressed(re);
        }

        if (error.getSuppressed().length > 0) {
            throw error;
        }
    }

    @Override
    public <T> void emit(final T event) {
        listeners.fire(this, event);
    }

    public void clearCache() {
        slowLookupMatchings.clear();
        listMatchings.clear();
    }

    private <T> Instance<T> doGetInstance(final boolean optional, final FusionBean<T> bean) {
        final var instance = contexts.findContext(bean.scope())
                .orElseThrow(() -> new MissingContextException(bean.scope().getName()))
                .getOrCreate(this, bean);
        return wrapIfNeeded(optional, instance);
    }

    @SuppressWarnings("unchecked")
    private <T> Instance<T> wrapIfNeeded(final boolean optional, final Instance<?> lookup) {
        if (optional) {
            return (Instance<T>) new OptionalInstance<>(lookup);
        }
        return (Instance<T>) lookup;
    }

    protected Types newTypes() {
        return new Types();
    }
}
