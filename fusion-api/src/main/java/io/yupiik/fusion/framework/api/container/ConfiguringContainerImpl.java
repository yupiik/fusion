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
package io.yupiik.fusion.framework.api.container;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.bean.ConfigurationBean;
import io.yupiik.fusion.framework.api.container.bean.ProvidedInstanceBean;
import io.yupiik.fusion.framework.api.container.context.ApplicationFusionContext;
import io.yupiik.fusion.framework.api.container.context.DefaultFusionContext;
import io.yupiik.fusion.framework.api.event.Emitter;
import io.yupiik.fusion.framework.api.lifecycle.Start;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.api.spi.FusionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;

public class ConfiguringContainerImpl implements ConfiguringContainer {
    private final Beans beans = new Beans();
    private final Contexts contexts = new Contexts();
    private final Listeners listeners = new Listeners();
    private final Collection<FusionModule> modules = new ArrayList<>();
    private boolean disableAutoDiscovery = false;
    private ClassLoader loader = Thread.currentThread().getContextClassLoader();

    @Override
    public RuntimeContainer start() {
        final var runtimeContainer = new RuntimeContainerImpl(beans, contexts, listeners);
        if (disableAutoDiscovery && modules.isEmpty()) {
            contexts.doRegister(new ApplicationFusionContext(), new DefaultFusionContext());
            beans.doRegister(defaultBeans(runtimeContainer).toArray(FusionBean<?>[]::new));
            runtimeContainer.clearCache();
            if (listeners.hasDirectListener(Start.class)) {
                listeners.fire(runtimeContainer, new Start());
            }
            return runtimeContainer;
        }

        final var modules = disableAutoDiscovery ?
                this.modules :
                loadModules()
                        .sorted(comparing(FusionModule::priority))
                        .toList();

        // beans
        beans.doRegister(filter(
                Stream.concat(
                        modules.stream()
                                .flatMap(FusionModule::beans),
                        defaultBeans(runtimeContainer)),
                modules.stream().map(FusionModule::beanFilter),
                runtimeContainer)
                .toArray(FusionBean<?>[]::new));

        // contexts
        contexts.doRegister(filter(
                Stream.concat(
                        // default scopes
                        Stream.of(new ApplicationFusionContext(), new DefaultFusionContext()),
                        // discovered ones (through module)
                        modules.stream().flatMap(FusionModule::contexts)),
                modules.stream().map(FusionModule::contextFilter),
                runtimeContainer)
                .toArray(FusionContext[]::new));

        // listeners
        listeners.doRegister(filter(
                modules.stream().flatMap(FusionModule::listeners),
                modules.stream().map(FusionModule::listenerFilter),
                runtimeContainer)
                .toArray(FusionListener[]::new));

        // startup event
        runtimeContainer.clearCache();
        if (listeners.hasDirectListener(Start.class)) {
            listeners.fire(runtimeContainer, new Start());
        }
        return runtimeContainer;
    }

    @Override
    public io.yupiik.fusion.framework.api.ConfiguringContainer disableAutoDiscovery(final boolean disableAutoDiscovery) {
        this.disableAutoDiscovery = disableAutoDiscovery;
        return this;
    }

    @Override
    public io.yupiik.fusion.framework.api.ConfiguringContainer loader(final ClassLoader loader) {
        this.loader = loader;
        return this;
    }

    @Override
    public io.yupiik.fusion.framework.api.ConfiguringContainer register(final FusionModule... modules) {
        this.modules.addAll(List.of(modules));
        return this;
    }

    @Override
    public io.yupiik.fusion.framework.api.ConfiguringContainer register(final FusionBean<?>... beans) {
        this.beans.doRegister(beans);
        return this;
    }

    @Override
    public io.yupiik.fusion.framework.api.ConfiguringContainer register(final FusionListener<?>... listeners) {
        this.listeners.doRegister(listeners);
        return this;
    }

    @Override
    public io.yupiik.fusion.framework.api.ConfiguringContainer register(final FusionContext... contexts) {
        this.contexts.doRegister(contexts);
        return this;
    }

    protected Stream<FusionModule> loadModules() {
        return ServiceLoader
                .load(FusionModule.class, loader).stream()
                .map(ServiceLoader.Provider::get);
    }

    protected Stream<FusionBean<?>> defaultBeans(final RuntimeContainer runtimeContainer) {
        return Stream.of(
                new ProvidedInstanceBean<>(ApplicationScoped.class, Emitter.class, () -> runtimeContainer),
                new ProvidedInstanceBean<>(DefaultScoped.class, RuntimeContainer.class, () -> runtimeContainer),
                new ConfigurationBean());
    }

    private <A> Stream<A> filter(final Stream<A> input, final Stream<BiPredicate<RuntimeContainer, A>> predicates,
                                 final RuntimeContainer runtimeContainer) {
        final var predicate = predicates.filter(Objects::nonNull).reduce(null, (a, b) -> a == null ? b : a.and(b));
        return predicate == null ? input : input.filter(it -> predicate.test(runtimeContainer, it));
    }
}
