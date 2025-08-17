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
import io.yupiik.fusion.framework.api.configuration.MissingRequiredParameterException;
import io.yupiik.fusion.framework.api.container.DefaultInstance;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;

public class BaseCliCommand<CF, C extends Runnable> implements CliCommand<C> {
    private final String name;
    private final String description;
    private final Function<Configuration, CF> configurationProvider;
    private final BiFunction<CF, List<Instance<?>>, C> constructor;
    private final List<Parameter> parameters;
    private final Map<String, String> metadata;

    public BaseCliCommand(final String name, final String description,
                          final Function<Configuration, CF> configurationProvider,
                          final BiFunction<CF, List<Instance<?>>, C> constructor,
                          final List<Parameter> parameters) {
        this(name, description, configurationProvider, constructor, parameters, Map.of());
    }

    public BaseCliCommand(final String name, final String description,
                          final Function<Configuration, CF> configurationProvider,
                          final BiFunction<CF, List<Instance<?>>, C> constructor,
                          final List<Parameter> parameters, final Map<String, String> metadata) {
        this.name = name;
        this.description = description;
        this.configurationProvider = configurationProvider;
        this.constructor = constructor;
        this.parameters = parameters;
        this.metadata = metadata;
    }

    @Override
    public Map<String, String> metadata() {
        return metadata;
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
        final C conf;
        try {
            conf = constructor.apply(configurationProvider.apply(configuration), dependents);
        } catch (final
        MissingRequiredParameterException missingRequiredParameterException) { // "No value for 'xxx.xxx'"
            final var rewritten = new MissingRequiredParameterException(
                    // format as an option and not a config/system prop
                    missingRequiredParameterException.getMessage().replace(" '", " '--").replace('.', '-') +
                            formatLightHelp());
            rewritten.setStackTrace(new StackTraceElement[0]);
            throw rewritten;
        }
        return new DefaultInstance<>(null, null, conf, dependents);
    }

    private String formatLightHelp() {
        return parameters.isEmpty() ? "" : ('\n' + parameters.stream()
                .sorted(comparing(Parameter::cliName))
                .map(p -> p.cliName() + ": " + p.description())
                .collect(joining("\n", "Available parameters:\n", "\n")));
    }

    public static class ContainerBaseCliCommand<CF, C extends Runnable> extends BaseCliCommand<CF, C> {
        public ContainerBaseCliCommand(final String name, final String description, final Function<Configuration, CF> configurationProvider,
                                       final BiFunction<CF, List<Instance<?>>, C> constructor, final List<Parameter> parameters, final Map<String, String> metadata) {
            super(name, description, configurationProvider, constructor, parameters, metadata);
        }

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
