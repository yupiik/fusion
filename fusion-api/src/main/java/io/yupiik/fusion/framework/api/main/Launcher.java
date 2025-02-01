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
package io.yupiik.fusion.framework.api.main;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.container.bean.ProvidedInstanceBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default launcher using {@link ConfiguringContainer}/{@code io.yupiik.fusion.framework.api.RuntimeContainer}.
 * It adds the notion of {@link Awaiter} to enable to not stop immediately when started.
 */
public class Launcher implements AutoCloseable {
    protected final RuntimeContainer container;
    private final Instance<List<Awaiter>> awaiters;
    private final AtomicBoolean isClosing = new AtomicBoolean(false);

    public Launcher(final String... args) {
        container = customize(ConfiguringContainer.of()
                .register(new ProvidedInstanceBean<>(DefaultScoped.class, Args.class, () -> new Args(List.of(args))))
                .register(new ProvidedInstanceBean<>(DefaultScoped.class, ArgsConfigSource.class, () -> new ArgsConfigSource(List.of(args)))))
                .start();
        awaiters = lookupAwaiters();
    }

    protected ConfiguringContainer customize(final ConfiguringContainer container) {
        return container;
    }

    public void await() {
        awaiters.instance().forEach(Awaiter::await);
    }

    @Override
    public void close() {
        if (!isClosing.compareAndSet(false, true)) {
            container.close();
        }
    }

    protected Instance<List<Awaiter>> lookupAwaiters() {
        return container.lookups(Awaiter.class, list -> list.stream().map(Instance::instance).toList());
    }

    public static void main(final String... args) {
        boolean useHook = false;
        Thread hook = null;
        try (final var launcher = new Launcher(args)) {
            Configuration configuration = launcher.container.lookup(Configuration.class).instance();

            useHook = Boolean.parseBoolean(configuration.get("fusion.launcher.useHook").orElse("false"));
            if (useHook) {
                hook = new Thread(launcher::close);
                Runtime.getRuntime().addShutdownHook(hook);
            }
            launcher.awaiters.instance().forEach(Awaiter::await);
        } finally {
            try {
                if (useHook) {
                    Runtime.getRuntime().removeShutdownHook(hook);
                }

            } catch (IllegalStateException e) {
                /* already shutting down */
            }
        }
    }
}
