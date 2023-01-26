/*
 * Copyright (c) 2022-2023 - Yupiik SAS - https://www.yupiik.com
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
package io.yupiik.fusion.testing.impl;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.testing.Fusion;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

public class FusionParameterResolver implements ParameterResolver, AfterEachCallback, TestInstancePostProcessor, AfterAllCallback {
    static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(FusionParameterResolver.class);

    @Override
    public boolean supportsParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext) throws ParameterResolutionException {
        final var parameter = parameterContext.getParameter();
        return parameter.isAnnotationPresent(Fusion.class) || parameter.getType() == RuntimeContainer.class;
    }

    @Override
    public Object resolveParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext) throws ParameterResolutionException {
        final var runtimeContainer = getContainer(extensionContext);
        if (parameterContext.getParameter().getType() == RuntimeContainer.class) {
            return runtimeContainer;
        }

        final var lookup = runtimeContainer.lookup(parameterContext.getParameter().getParameterizedType());
        extensionContext
                .getStore(NAMESPACE)
                .getOrComputeIfAbsent(MethodCleanBag.class, k -> new MethodCleanBag(), MethodCleanBag.class)
                .instances.add(lookup);
        return lookup.instance();
    }

    @Override
    public void afterEach(final ExtensionContext context) {
        destroyCleanBag(context, MethodCleanBag.class);
    }

    @Override
    public void afterAll(final ExtensionContext context) {
        destroyCleanBag(context, CleanBag.class);
    }

    @Override
    public void postProcessTestInstance(final Object testInstance, final ExtensionContext context) {
        doInject(testInstance, testInstance.getClass(), context);
    }

    private RuntimeContainer getContainer(final ExtensionContext extensionContext) {
        return extensionContext
                .getStore(NAMESPACE)
                .get(RuntimeContainer.class, RuntimeContainer.class);
    }

    private void doInject(final Object testInstance, final Class<?> aClass, final ExtensionContext context) {
        if (aClass == null || aClass == Object.class) {
            return;
        }

        Stream.of(aClass.getDeclaredFields())
                .filter(it -> it.isAnnotationPresent(Fusion.class))
                .peek(it -> it.setAccessible(true))
                .forEach(it -> {
                    try {
                        final var lookup = getContainer(context).lookup(it.getGenericType());
                        context.getStore(NAMESPACE)
                                .getOrComputeIfAbsent(CleanBag.class, k -> new CleanBag(), CleanBag.class)
                                .instances.add(lookup);
                        it.set(Modifier.isStatic(it.getModifiers()) ? null : testInstance, lookup.instance());
                    } catch (final IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                });
        doInject(testInstance, aClass.getSuperclass(), context);
    }

    private void destroyCleanBag(final ExtensionContext context, final Class<? extends CleanBag> type) {
        ofNullable(context.getStore(NAMESPACE).get(type, type))
                .ifPresent(c -> c.instances.forEach(Instance::close));
    }

    private static class CleanBag {
        protected final List<Instance<?>> instances = new ArrayList<>();
    }

    private static class MethodCleanBag extends CleanBag {
    }
}
