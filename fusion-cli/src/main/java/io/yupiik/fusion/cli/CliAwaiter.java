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
package io.yupiik.fusion.cli;

import io.yupiik.fusion.cli.internal.CliCommand;
import io.yupiik.fusion.cli.internal.CliCommandResolver;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.main.Args;
import io.yupiik.fusion.framework.api.main.Awaiter;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static java.util.Comparator.comparing;
import static java.util.Optional.empty;

@DefaultScoped
public class CliAwaiter implements Awaiter {
    private final Args args;
    private final List<CliCommand<? extends Runnable>> commands;
    private final CliCommandResolver resolver;
    private final Configuration configuration;

    public CliAwaiter(final Args args,
                      final Configuration configuration,
                      final List<CliCommand<? extends Runnable>> commands) {
        this.args = args;
        this.configuration = configuration;
        this.commands = commands;
        this.resolver = new CliCommandResolver(this.commands);
    }

    public static CliAwaiter of(final Args args,
                                final Configuration configuration,
                                final Map<String, CliCommand<? extends Runnable>> commands) {
        return new CliAwaiter(args, configuration, List.copyOf(commands.values()));
    }

    @Override
    public void await() {
        final var tokens = args.args();
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("Ensure to call a command:\n" + usage());
        }

        final var resolution = resolver.resolve(tokens);
        if (resolution.isExact()) {
            final var command = resolution.command();
            final var commandArgs = tokens.subList(resolution.commandLen(), tokens.size());
            if (commandArgs.equals(List.of("--help"))) {
                throw new IllegalArgumentException(optionsFor(command));
            }
            run(command, commandArgs);
            return;
        }
        if (resolution.isGroup()) {
            final var rest = tokens.subList(resolution.groupLen(), tokens.size());
            if (rest.isEmpty() || rest.equals(List.of("--help"))) {
                throw new IllegalArgumentException(usage(resolution.group()));
            }
            throw new IllegalArgumentException("Missing command '" + rest.get(0) + "' in group '" + String.join("/", resolution.group()) + "':\n" + usage(resolution.group()));
        }

        final var first = tokens.get(0);
        if ("help".equals(first) || "--help".equals(first)) {
            if (tokens.size() == 1) {
                throw new IllegalArgumentException(usage());
            }
            final var target = resolver.resolve(tokens.subList(1, tokens.size()));
            if (target.isExact()) {
                throw new IllegalArgumentException(optionsFor(target.command()));
            }
            if (target.isGroup()) {
                throw new IllegalArgumentException(usage(target.group()));
            }
            throw new IllegalArgumentException(usage());
        }
        throw new IllegalArgumentException("Missing command '" + first + "':\n" + usage());
    }

    private void run(final CliCommand<? extends Runnable> command, final List<String> commandArgs) {
        final var keyMapper = command.keyMapper();
        try (final var instance = command.create(key -> doFindConf(command, commandArgs, keyMapper.apply(key)), new ArrayList<>())) {
            instance.instance().run();
        }
    }

    private Optional<String> doFindConf(final CliCommand<? extends Runnable> command, final List<String> commandArgs, final String key) {
        final var idx = commandArgs.indexOf(key);
        if (idx >= 0 && commandArgs.size() > idx) {
            return Optional.of(commandArgs.get(idx + 1));
        }
        // try short name
        if (key.startsWith(command.cliPrefix()) && key.length() > command.cliPrefix().length()) {
            return doFindConf(command, commandArgs, "--" + key.substring(command.cliPrefix().length()));
        }
        return configuration.get(key)
                .or(() -> key.startsWith("--") ? configuration.get(key.substring("--".length())) : empty());
    }

    public String usage() {
        final var showParams = configuration.get("fusion.cli.usage.parameters").map(Boolean::parseBoolean).orElse(true);
        final var sorted = commands.stream()
                .sorted(comparing(CliCommand::name))
                .toList();
        final var cmdMaxLen = sorted.stream().mapToInt(c -> c.name().length()).max().orElse(0);
        final var fmtCmd = "  %-" + cmdMaxLen + "s    %s";
        final var out = new StringBuilder("Commands:\n");
        sorted.forEach(c -> out.append(String.format(fmtCmd, c.name(), c.description())).append('\n'));
        if (showParams) {
            for (final var c : sorted) {
                if (!c.parameters().isEmpty()) {
                    out.append('\n').append(optionsFor(c));
                }
            }
        }
        return out.toString();
    }

    /**
     * @param group the group path segments, {@code null} or empty for the global usage.
     * @return the usage of the direct subcommands of the group (implicit intermediate groups are marked {@code (group)}).
     */
    public String usage(final List<String> group) {
        final var groupLen = group.size();
        final var children = new TreeMap<String, String>();
        for (final var command : commands) {
            final var path = List.of(command.path());
            if (path.size() <= groupLen || !path.subList(0, groupLen).equals(group)) {
                continue;
            }
            final var child = path.get(groupLen);
            final var childPath = String.join("/", path.subList(0, groupLen + 1));
            if (path.size() == groupLen + 1) {
                children.put(childPath, command.description());
            } else {
                children.putIfAbsent(childPath, "(group)");
            }
        }
        final var out = new StringBuilder("Commands in '" + String.join("/", group) + "':\n");
        final var childMaxLen = children.keySet().stream().mapToInt(String::length).max().orElse(0);
        final var fmtCmd = "  %-" + childMaxLen + "s    %s";
        children.forEach((child, description) -> out.append(String.format(fmtCmd, child, description)).append('\n'));
        return out.toString();
    }

    public String optionsFor(final CliCommand<? extends Runnable> command) {
        final var paramMaxLen = command.parameters().stream()
                .mapToInt(p -> displayName(command.cliPrefix(), p.cliName()).length())
                .max().orElse(0);
        final var fmtParam = "    %-" + paramMaxLen + "s    %s";
        final var out = new StringBuilder("Options for '").append(command.name()).append("':\n");
        command.parameters().stream()
                .sorted(comparing(CliCommand.Parameter::cliName))
                .forEach(p -> out.append(String.format(fmtParam, displayName(command.cliPrefix(), p.cliName()),
                        p.description() == null || p.description().isBlank() ? "-" : p.description())).append('\n'));
        return out.toString();
    }

    private static String displayName(final String cmdPrefix, final String cliName) {
        return cliName.startsWith(cmdPrefix) ? "--" + cliName.substring(cmdPrefix.length()) : cliName;
    }
}
