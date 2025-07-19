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
package io.yupiik.fusion.testing.impl;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.container.bean.ProvidedInstanceBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.testing.FusionSupport;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.AnnotationUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

public class FusionPerClassLifecycle extends FusionParameterResolver implements BeforeAllCallback, AfterAllCallback {
    @Override
    public void beforeAll(final ExtensionContext context) {
        context.getStore(NAMESPACE).getOrComputeIfAbsent(RuntimeContainer.class, k -> {
            final var container = ConfiguringContainer.of();
            AnnotationUtils.findAnnotation(context.getTestClass(), FusionSupport.class)
                    .ifPresent(conf -> {
                        container.disableAutoDiscovery(conf.disableDiscovery());
                        container.register(Stream.of(conf.modules())
                                .map(it -> {
                                    try {
                                        return it.asSubclass(FusionModule.class).getConstructor().newInstance();
                                    } catch (final InstantiationException | IllegalAccessException |
                                                   NoSuchMethodException e) {
                                        throw new IllegalStateException(e);
                                    } catch (final InvocationTargetException e) {
                                        throw new IllegalStateException(e.getTargetException());
                                    }
                                })
                                .toArray(FusionModule[]::new));
                        container.register(new ProvidedInstanceBean<>(DefaultScoped.class, ExtensionContext.class, () -> context));
                    });
            return container.start();
        });
    }

    @Override
    public void afterAll(final ExtensionContext context) {
        super.afterAll(context);
        ofNullable(context.getStore(NAMESPACE).get(RuntimeContainer.class, RuntimeContainer.class))
                .ifPresent(RuntimeContainer::close);
    }
}
