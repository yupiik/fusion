package io.yupiik.fusion.cli;

import io.yupiik.fusion.cli.internal.CliCommand;
import io.yupiik.fusion.framework.api.main.Args;
import io.yupiik.fusion.framework.api.main.Awaiter;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

@DefaultScoped
public class CliAwaiter implements Awaiter {
    private final Args args;
    private final Map<String, CliCommand<? extends Runnable>> commands;

    public CliAwaiter(final Args args, final List<CliCommand<? extends Runnable>> commands) {
        this.args = args;
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

        try (final var instance = command.create(key -> {
            // assume args are in this form:
            // $ --<conf name> <value>
            // we support "-" for the root configuration key which enables to drop the prefix
            return Stream.of(
                            "--" + key,
                            "--" + key.replace('.', '-').replace("--", "-"))
                    .mapToInt(it -> args.args().indexOf(it))
                    .filter(i -> i >= 0 && args.args().size() > i)
                    .mapToObj(i -> ofNullable(args.args().get(i + 1)))
                    .findFirst()
                    .orElseGet(Optional::empty);
        }, new ArrayList<>())) {
            instance.instance().run();
        }
    }

    // todo: reflow (max 100 chars of width?)
    private String usage() {
        return commands.values().stream()
                .sorted(comparing(CliCommand::name))
                .map(c -> "" +
                        c.name() + ":\n" +
                        "  " + c.description())
                .collect(joining("\n"));
    }
}
