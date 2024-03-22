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
package io.yupiik.fusion.testing.task;

import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.exception.NoMatchingBeanException;
import io.yupiik.fusion.testing.impl.FusionParameterResolver;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.yupiik.fusion.testing.task.Task.Phase.AFTER;
import static io.yupiik.fusion.testing.task.Task.Phase.BEFORE;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static org.junit.platform.commons.support.AnnotationSupport.findRepeatableAnnotations;

public class TaskExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(TaskExtension.class);

    @Override
    public void beforeEach(final ExtensionContext ctx) {
        final var items = findRepeatableAnnotations(ctx.getElement(), Task.class);
        if (items.isEmpty()) {
            return;
        }

        final var container = ctx.getStore(ExtensionContext.Namespace.create(FusionParameterResolver.class))
                .get(RuntimeContainer.class, RuntimeContainer.class);
        final var store = ctx.getStore(NAMESPACE);
        final var holder = new Holder(items, container, store, new HashMap<>());
        holder.run(BEFORE);
        store.put(Holder.class, holder);
    }

    @Override
    public void afterEach(final ExtensionContext ctx) {
        ofNullable(ctx.getStore(NAMESPACE).get(Holder.class, Holder.class))
                .ifPresent(holder -> holder.run(AFTER));
    }

    @Override
    public boolean supportsParameter(final ParameterContext parameterContext, final ExtensionContext ctx) throws ParameterResolutionException {
        return parameterContext.getParameter().isAnnotationPresent(TaskResult.class) && resolveParam(parameterContext, ctx) != null;
    }

    @Override
    public Object resolveParameter(final ParameterContext parameterContext, final ExtensionContext ctx) throws ParameterResolutionException {
        final var param = resolveParam(parameterContext, ctx);
        if (param == null) {
            throw new ParameterResolutionException("No parameter for " + parameterContext.getParameter().getType());
        }
        return param;
    }

    private Object resolveParam(final ParameterContext parameterContext, final ExtensionContext ctx) {
        return ctx.getStore(NAMESPACE).get(Holder.class, Holder.class)
                .params
                .get(parameterContext.getParameter().getAnnotation(TaskResult.class).value());
    }

    private record Holder(Collection<Task> data, RuntimeContainer container, ExtensionContext.Store store,
                          Map<Class<?>, Object> params) {
        private void run(final Task.Phase phase) {
            params.putAll(data.stream()
                    .filter(it -> it.phase() == phase)
                    .collect(toMap(
                            Task::value,
                            it -> ofNullable(doRun(it.value(), map(it.properties()))).orElseGet(Object::new), (a, b) -> b)));
        }

        @SuppressWarnings("rawtypes")
        private Object doRun(final Class<? extends Task.Supplier> it, final Map<String, String> properties) {
            if (container == null) {
                return executeStandaloneSupplier(it, properties, () -> new IllegalArgumentException("Can't instantiate '" + it.getName() + "'"));
            }
            try (final var instance = container.lookup(it)) {
                final Task.Supplier<?> supplier = instance.instance();
                supplier.init(properties);
                return supplier.get();
            } catch (final NoMatchingBeanException nmbe) {
                return executeStandaloneSupplier(it, properties, () -> nmbe);
            }
        }

        @SuppressWarnings("rawtypes")
        private Object executeStandaloneSupplier(final Class<? extends Task.Supplier> it,
                                                 final Map<String, String> properties,
                                                 final Supplier<RuntimeException> error) {
            try { // try standalone class
                final Task.Supplier<?> supplier = it.getConstructor().newInstance();
                supplier.init(properties);
                return supplier.get();
            } catch (final InstantiationException | IllegalAccessException |
                           InvocationTargetException | NoSuchMethodException e) {
                throw error.get();
            }
        }

        private Map<String, String> map(final Task.Property[] properties) {
            if (properties.length == 0) {
                return Map.of();
            }
            return Stream.of(properties).collect(toMap(Task.Property::name, Task.Property::value));
        }
    }
}
