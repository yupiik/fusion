package io.yupiik.fusion.cli;

import io.yupiik.fusion.cli.internal.CliCommand;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.main.Args;
import io.yupiik.fusion.framework.api.main.Awaiter;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Comparator.comparing;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

@DefaultScoped
public class CliAwaiter implements Awaiter {
    private final Args args;
    private final Map<String, CliCommand<? extends Runnable>> commands;
    private final Configuration configuration;

    public CliAwaiter(final Args args,
                      final Configuration configuration,
                      final List<CliCommand<? extends Runnable>> commands) {
        this.args = args;
        this.configuration = configuration;
        this.commands = commands.stream().collect(toMap(CliCommand::name, identity()));
    }

    @Override
    public void await() {
        if (args.args().isEmpty()) {
            throw new IllegalArgumentException("Ensure to call a command:\n" + usage());
        }
        final var cmdName = args.args().get(0);
        final var command = commands.get(cmdName);
        if (command == null) {
            throw new IllegalArgumentException("Missing command '" + cmdName + "':\n" + usage());
        }

        final var keyMapping = command.parameters().stream()
                // todo: list?
                .collect(toMap(CliCommand.Parameter::configName, CliCommand.Parameter::cliName, (a, b) -> a));
        try (final var instance = command.create(key -> findConfigInArgs(keyMapping, key), new ArrayList<>())) {
            instance.instance().run();
        }
    }

    private Optional<String> findConfigInArgs(final Map<String, String> keyMapping, final String key) {
        final var value = keyMapping.get(key);
        if (value == null) {
            // can be a list, if so just reformat the keys lazily
            final var formattedKey = (key.startsWith("-.") ? "" : "--") + key.replace('.', '-');
            return doFindConf(formattedKey);
        }
        return doFindConf(value);
    }

    private Optional<String> doFindConf(final String key) {
        final var idx = args.args().indexOf(key);
        if (idx >= 0 && args.args().size() > idx) {
            return of(args.args().get(idx + 1));
        }
        return empty();
    }

    // todo: reflow (max 100 chars of width?)
    private String usage() {
        return commands.values().stream()
                .sorted(comparing(CliCommand::name))
                .map(c -> "" +
                        "* " + c.name() + ":\n" +
                        "  " + c.description() +
                        (configuration.get("fusion.cli.usage.parameters").map(Boolean::parseBoolean).orElse(true) ?
                                c.parameters().isEmpty() ? "" : c.parameters().stream()
                                        .map(p -> "    " + p.cliName() + ": " + (p.description() == null || p.description().isBlank() ? "-" : p.description()))
                                        .collect(joining("\n", "\n  Parameters:\n", "")) : ""))
                .collect(joining("\n", "", "\n"));
    }
}
