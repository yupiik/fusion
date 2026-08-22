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

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static java.util.Locale.ROOT;

/**
 * Describes the shell(s) for which the CLI should register a completion command.
 * <p>
 * The effective value comes from the {@code fusion.cli.shell} configuration key (system property
 * {@code fusion.cli.shell} or {@code FUSION_CLI_SHELL} environment variable). It accepts:
 * <ul>
 *     <li>{@code all} - register the completion command for every supported shell,</li>
 *     <li>{@code none} - register no completion command,</li>
 *     <li>{@code bash}, {@code zsh} or {@code powershell} - restrict to that shell,</li>
 *     <li>absent - detect the current shell from the environment ({@code bash} when inconclusive).</li>
 * </ul>
 */
public enum CliShell {
    BASH("bash", CompletionBashCommand::new),
    ZSH("zsh", CompletionZshCommand::new),
    POWERSHELL("powershell", CompletionPowerShellCommand::new);

    private final String id;
    private final Supplier<BaseCompletionCliCommand> command;

    CliShell(final String id, final Supplier<BaseCompletionCliCommand> command) {
        this.id = id;
        this.command = command;
    }

    /**
     * @return the completion command bean for this shell.
     */
    public BaseCompletionCliCommand newCommand() {
        return command.get();
    }

    /**
     * Selects the shells to register.
     *
     * @param override the {@code fusion.cli.shell} value, already resolved from property/env (may be blank).
     * @param shell    the {@code $SHELL} environment value (may be null).
     * @param windows  whether the JVM is running on Windows.
     * @return the shells to register, possibly empty.
     */
    public static List<CliShell> select(final String override, final String shell, final boolean windows) {
        final var effective = override == null ? "" : override.toLowerCase(ROOT).trim();
        switch (effective) {
            case "all":
                return List.of(BASH, ZSH, POWERSHELL);
            case "none":
                return List.of();
            case "bash":
            case "zsh":
            case "powershell":
                return List.of(byId(effective));
            default:
                return List.of(detect(shell, windows));
        }
    }

    private static CliShell detect(final String shell, final boolean windows) {
        if (shell != null) {
            final var lower = shell.toLowerCase(ROOT);
            if (lower.contains("zsh")) {
                return ZSH;
            }
            if (lower.contains("bash") || lower.contains("sh")) {
                return BASH;
            }
        }
        return windows ? POWERSHELL : BASH;
    }

    private static CliShell byId(final String id) {
        for (final var value : values()) {
            if (value.id.equals(id)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unsupported shell '" + id + "', expected one of all, none, "
                + String.join(",", List.of(BASH.id, ZSH.id, POWERSHELL.id)));
    }

    @Override
    public String toString() {
        return id;
    }
}