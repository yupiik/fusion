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

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionModule;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.function.Predicate;
import java.util.stream.Stream;

public class FusionMonoLifecycle extends FusionParameterResolver implements BeforeAllCallback {
    private static volatile RuntimeContainer INSTANCE;

    @Override
    public void beforeAll(final ExtensionContext context) {
        if (INSTANCE == null) {
            synchronized (FusionMonoLifecycle.class) {
                if (INSTANCE == null) {
                    final var container = ConfiguringContainer.of();
                    final var excludedModules = System.getProperty("yupiik.fusion.mono.modules.discovery.excluded");
                    if (excludedModules != null) {
                        final var loader = Thread.currentThread().getContextClassLoader();
                        Stream.of(excludedModules.split(","))
                                .map(String::strip)
                                .filter(Predicate.not(String::isBlank))
                                .<Class<? extends FusionModule>>map(it -> {
                                    try {
                                        return loader.loadClass(it).asSubclass(FusionModule.class);
                                    } catch (final ClassNotFoundException e) {
                                        throw new IllegalArgumentException("Can't load module '" + it + "'", e);
                                    }
                                })
                                .forEach(container::disableModule);
                    }
                    INSTANCE = container.start();
                    Runtime.getRuntime().addShutdownHook(new Thread(INSTANCE::close, getClass().getName() + "-shutdown"));
                }
            }
        }
        context.getStore(NAMESPACE).getOrComputeIfAbsent(RuntimeContainer.class, k -> INSTANCE);
    }
}
