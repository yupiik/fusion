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
package io.yupiik.fusion.cli.internal;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.container.DefaultInstance;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public class BaseCliCommand<CF, C extends Runnable> implements CliCommand<C> {
    private final String name;
    private final String description;
    private final Function<Configuration, CF> configurationProvider;
    private final BiFunction<CF, List<Instance<?>>, C> constructor;
    private final List<Parameter> parameters;

    public BaseCliCommand(final String name, final String description,
                          final Function<Configuration, CF> configurationProvider,
                          final BiFunction<CF, List<Instance<?>>, C> constructor,
                          final List<Parameter> parameters) {
        this.name = name;
        this.description = description;
        this.configurationProvider = configurationProvider;
        this.constructor = constructor;
        this.parameters = parameters;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public List<Parameter> parameters() {
        return parameters;
    }

    @Override
    public Instance<C> create(final Configuration configuration, final List<Instance<?>> dependents) {
        return new DefaultInstance<>(
                null, null,
                constructor.apply(configurationProvider.apply(configuration), dependents),
                dependents);
    }

    public static class ContainerBaseCliCommand<CF, C extends Runnable> extends BaseCliCommand<CF, C> {
        public ContainerBaseCliCommand(final String name, final String description, final Function<Configuration, CF> configurationProvider,
                                       final BiFunction<CF, List<Instance<?>>, C> constructor, final List<Parameter> parameters) {
            super(name, description, configurationProvider, constructor, parameters);
        }

        protected static <T> T lookup(final RuntimeContainer container, final Class<T> type, final List<Instance<?>> deps) {
            final var i = container.lookup(type);
            deps.add(i);
            return i.instance();
        }
    }
}
