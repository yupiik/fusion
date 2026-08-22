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
import java.util.Map;

/**
 * Builds lightweight {@link CliCommand} fixtures for unit tests, mirroring the shape the
 * annotation processor generates for a {@code @Command}/{@code @RootConfiguration} pair.
 */
public final class CliCommandFixture {
    private CliCommandFixture() {
        // no-op
    }

    public static List<CliCommand<? extends Runnable>> commands() {
        return List.of(
                command("init", "Create config + database and migrate."),
                command("discover", "Discover new companies.", "limit", "country"),
                command(new String[]{"scan", "all"}, "Scan all companies.", "limit", "dry-run"),
                command(new String[]{"scan", "company"}, "Scan a company by id.", "id"),
                command(new String[]{"scan", "fill"}, "Resolve missing websites.", "limit", "method"),
                command(new String[]{"completion", "bash"}, "Print a bash completion script."));
    }

    private static CliCommand<? extends Runnable> command(final String command, final String description, final String... options) {
        return command(new String[]{command}, description, options);
    }

    private static CliCommand<? extends Runnable> command(final String[] path, final String description, final String... options) {
        final var parameters = List.of(options).stream()
                .map(opt -> {
                    final var cliName = opt.startsWith("--") ? opt : "--" + opt;
                    return new CliCommand.Parameter(cliName, cliName, cliName);
                })
                .toList();
        return new BaseCliCommand<>(path, description,
                conf -> null,
                (conf, deps) -> (Runnable) () -> {},
                parameters,
                Map.of());
    }
}