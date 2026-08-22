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
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toSet;

/**
 * Base {@link Bean} producing a {@link CliCommand} that prints a shell completion script.
 * Subclasses set the command path/description and the script body; shared helpers for
 * command-tree navigation and candidate computation are exposed as {@code protected} methods.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class BaseCompletionCliCommand extends BaseBean<CliCommand> {
    protected BaseCompletionCliCommand() {
        super(CliCommand.class, DefaultScoped.class, 1000, Map.of());
    }

    /**
     * @return the subcommand path, e.g. {@code {completion, bash}}.
     */
    protected abstract String[] path();

    /**
     * @return a description telling the user how to use the generated script.
     */
    protected abstract String description();

    /**
     * Writes the shell-specific output to {@link System#out}.
     *
     * @param commands      the registered commands.
     * @param configuration the effective configuration (app name, comp.line/comp.point cursor overrides).
     */
    protected abstract void write(List<CliCommand<? extends Runnable>> commands, Configuration configuration);

    @Override
    public CliCommand create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        return new BaseCliCommand<>(
                path(), description(),
                identity(),
                (final Configuration conf, final List<Instance<?>> deps) -> (Runnable) () -> {
                    // the returned Instance is added to the execution dependents so it is closed with the command
                    final var commands = container.lookups(CliCommand.class, BaseCompletionCliCommand::toCommands);
                    deps.add(commands);
                    write(commands.instance(), conf);
                },
                List.of(),
                Map.of());
    }

    private static List<CliCommand<? extends Runnable>> toCommands(final List<Instance<CliCommand>> instances) {
        return instances.stream().map(BaseCompletionCliCommand::cast).toList();
    }

    @SuppressWarnings("unchecked")
    private static CliCommand<? extends Runnable> cast(final Instance<CliCommand> instance) {
        return (CliCommand<? extends Runnable>) instance.instance();
    }

    /**
     * @return the completion function name for {@code app}.
     */
    protected String funcName(final String app) {
        return "_" + app.replace('-', '_') + "_complete";
    }

    /**
     * @return the application name used in scripts, from {@code fusion.cli.app.name} or {@code app}.
     */
    protected String appName(final Configuration configuration) {
        return configuration.get("fusion.cli.app.name").orElse("app");
    }

    /**
     * Direct child segments of {@code prefix}: the next {@code path()} segment of every command
     * starting (but not ending) with {@code prefix}.
     */
    protected Stream<String> children(final List<CliCommand<? extends Runnable>> commands, final List<String> prefix) {
        return commands.stream()
                .flatMap(it -> {
                    final var path = List.of(it.path());
                    if (path.size() <= prefix.size() || !path.subList(0, prefix.size()).equals(prefix)) {
                        return Stream.empty();
                    }
                    return Stream.of(path.get(prefix.size()));
                })
                .collect(toSet())
                .stream();
    }

    /**
     * @return the command whose {@code path()} is exactly {@code tokens}, or {@code null}.
     */
    protected CliCommand<? extends Runnable> resolved(final List<CliCommand<? extends Runnable>> commands, final List<String> tokens) {
        for (final var command : commands) {
            if (List.of(command.path()).equals(tokens)) {
                return command;
            }
        }
        return null;
    }

    /**
     * Computes shell-agnostic candidates: subcommand segments while the path is incomplete,
     * then the {@code --options} of the resolved leaf command.
     *
     * @param typed the words typed so far (excludes the current word being completed).
     * @param cur   the current partial word (may be empty).
     */
    protected List<String> candidates(final List<CliCommand<? extends Runnable>> commands, final List<String> typed, final String cur) {
        final var leaf = resolved(commands, typed);
        if (leaf != null) {
            return options(leaf, cur);
        }
        if (typed.isEmpty()) {
            return startsWith(commands.stream().map(it -> it.path()[0]).distinct().sorted().toList(), cur);
        }
        return startsWith(children(commands, typed).sorted().toList(), cur);
    }

    private List<String> options(final CliCommand<? extends Runnable> command, final String cur) {
        final var prefix = command.cliPrefix();
        return startsWith(command.parameters().stream()
                .map(p -> displayName(prefix, p.cliName()))
                .sorted()
                .toList(), cur);
    }

    private List<String> startsWith(final List<String> names, final String cur) {
        return names.stream().filter(it -> it.startsWith(cur)).toList();
    }

    protected String displayName(final String cmdPrefix, final String cliName) {
        return cliName.startsWith(cmdPrefix) ? "--" + cliName.substring(cmdPrefix.length()) : cliName;
    }

    protected String commandPath(final CliCommand<? extends Runnable> command) {
        return String.join(" ", command.path());
    }

    /**
     * @return the sorted option names ({@code --flag}) of a command.
     */
    protected List<String> optionNames(final CliCommand<? extends Runnable> command) {
        final var prefix = command.cliPrefix();
        return command.parameters().stream()
                .map(p -> displayName(prefix, p.cliName()))
                .sorted()
                .toList();
    }
}