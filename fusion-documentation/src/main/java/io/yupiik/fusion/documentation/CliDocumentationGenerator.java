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
package io.yupiik.fusion.documentation;

import io.yupiik.fusion.cli.internal.CliCommand;
import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.configuration.ConfigurationSource;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.container.bean.ProvidedInstanceBean;
import io.yupiik.fusion.framework.api.lifecycle.Start;
import io.yupiik.fusion.framework.api.lifecycle.Stop;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

public class CliDocumentationGenerator implements Runnable {
    private final Path source;
    private final Map<String, String> configuration;

    public CliDocumentationGenerator(final Path sourceBase, final Map<String, String> configuration) {
        this.source = sourceBase;
        this.configuration = configuration == null ? Map.of() : configuration;
    }

    @Override
    public void run() {
        final var packageFilter = configuration.getOrDefault("package", "");

        final List<Command> commands;
        try (final var container = ConfiguringContainer.of()
                .register(new ProvidedInstanceBean<>(ApplicationScoped.class, ConfigurationSource.class, () -> new ConfigurationSource() {
                    @Override
                    public String get(final String key) {
                        return CliDocumentationGenerator.class.getName().equals(key) ? "true" : null;
                    }
                }))
                .register(new FusionModule() { // disable start/stop events to not have code launched if used
                    @Override
                    public BiPredicate<RuntimeContainer, FusionListener<?>> listenerFilter() {
                        return (r, l) -> !(l.eventType() == Start.class || l.eventType() == Stop.class);
                    }
                })
                .start();
             final var instances = container.lookups(CliCommand.class, it -> it.stream()
                     .filter(c -> packageFilter.isBlank() || c.bean().type().getTypeName().startsWith(packageFilter))
                     .map(Instance::instance)
                     .map(c -> new Command(c.name(), c.description(), ((CliCommand<?>) c).parameters()))
                     .sorted(comparing(Command::name))
                     .toList())) {
            commands = instances.instance();
        }
        if (commands.isEmpty()) {
            return; // todo: fail?
        }
        try {
            final var base = ofNullable(configuration.get("outputBase"))
                    .map(Path::of)
                    .orElseGet(() -> source.resolve("content/cli"));
            Files.createDirectories(base);
            Files.writeString(base.resolve("index.adoc"), generateIndex(commands));
            final var app = configuration.getOrDefault("application", "java ... io.yupiik.fusion.framework.api.main.Launcher");
            for (final var command : commands) {
                Files.writeString(base.resolve(command.name() + ".adoc"), generateDetail(app, command));
            }

        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private CharSequence generateDetail(final String app, final Command command) {
        return "= " + command.name() + "\n" +
                "\n" +
                "== Description\n" +
                "\n" +
                command.description() + "\n" +
                "\n" +
                "== Synopsis\n" +
                "\n" +
                """
                        [source,bash]
                        .${name}-sample
                        ----
                        """.replace("${name}", command.name()) +
                app + " \\\n" +
                "    " + command.name + (command.parameters().isEmpty() ? "" : " \\") + '\n' +
                command.parameters().stream().map(it -> "    " + it.cliName() + " ...").collect(joining("\\\n", "", "\n")) +
                "----\n" +
                "\n" +
                "== Parameters\n" +
                "\n" +
                (command.parameters().isEmpty() ? "No parameter." : command.parameters().stream()
                        .map(p -> p.cliName() + "::\n" + p.description() + "\n")
                        .collect(joining("\n")));
    }

    private CharSequence generateIndex(final List<Command> commands) {
        return commands.stream()
                .map(it -> "== " + it.name() + "\n\n" + it.description() + "\n\nSee xref:" + it.name() + ".adoc[" + it.name() + "] detail page.\n")
                .collect(joining("\n"));
    }

    private record Command(String name, String description, List<CliCommand.Parameter> parameters) {
    }
}
