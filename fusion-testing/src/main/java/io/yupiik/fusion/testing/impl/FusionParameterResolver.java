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
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.util.ArrayList;
import java.util.List;

import static java.util.Optional.ofNullable;

public class FusionParameterResolver implements ParameterResolver, AfterEachCallback {
    static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(FusionParameterResolver.class);

    @Override
    public boolean supportsParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext) throws ParameterResolutionException {
        final var parameter = parameterContext.getParameter();
        return parameter.isAnnotationPresent(Fusion.class) || parameter.getType() == RuntimeContainer.class;
    }

    @Override
    public Object resolveParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext) throws ParameterResolutionException {
        final var runtimeContainer = extensionContext
                .getStore(NAMESPACE)
                .get(RuntimeContainer.class, RuntimeContainer.class);
        if (parameterContext.getParameter().getType() == RuntimeContainer.class) {
            return runtimeContainer;
        }

        final var lookup = runtimeContainer.lookup(parameterContext.getParameter().getParameterizedType());
        extensionContext.getStore(NAMESPACE).getOrComputeIfAbsent(CleanBag.class, k -> new CleanBag(), CleanBag.class).instances.add(lookup);
        return lookup.instance();
    }

    @Override
    public void afterEach(final ExtensionContext context) {
        ofNullable(context.getStore(NAMESPACE).get(CleanBag.class, CleanBag.class))
                .ifPresent(c -> c.instances.forEach(Instance::close));
    }

    private static class CleanBag {
        private final List<Instance<?>> instances = new ArrayList<>();
    }
}
