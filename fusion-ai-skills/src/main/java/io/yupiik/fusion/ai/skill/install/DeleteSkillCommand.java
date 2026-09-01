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
package io.yupiik.fusion.ai.skill.install;

import io.yupiik.fusion.framework.build.api.cli.Command;
import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

@Command(name = "delete", description = "Remove the Fusion AI skills previously installed for a coding agent.")
public class DeleteSkillCommand implements Runnable {
    private final Conf conf;

    public DeleteSkillCommand(final Conf conf) {
        this.conf = conf;
    }

    @Override
    public void run() {
        final var agent = conf.agent().toLowerCase(Locale.ROOT);
        if (!agent.equals("opencode") && !agent.equals("claude") && !agent.equals("generic")) {
            throw new IllegalArgumentException("Unsupported agent '" + conf.agent()
                    + "', expected one of: opencode, claude, generic.");
        }
        final var target = Optional.ofNullable(conf.target())
                .filter(it -> !it.isBlank())
                .map(Path::of)
                .orElseGet(() -> defaultTarget(agent, Optional.ofNullable(conf.scope()).orElse("local")));
        if (!Files.isDirectory(target)) {
            System.err.println("Nothing to delete, no skills installed at: " + target);
            return;
        }
        var count = 0;
        try (final var installed = Files.list(target)) {
            final var it = installed.iterator();
            while (it.hasNext()) {
                final var skillDir = it.next();
                if (Files.isRegularFile(skillDir.resolve("SKILL.md"))) {
                    try (final var files = Files.walk(skillDir)) {
                        files.sorted(Comparator.reverseOrder()).forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (final IOException e) {
                                throw new IllegalStateException("Cannot delete " + p + ": " + e.getMessage(), e);
                            }
                        });
                    }
                    System.out.println(" - " + skillDir.getFileName() + " removed from " + target);
                    count++;
                }
            }
        } catch (final IOException e) {
            throw new IllegalStateException("Cannot delete skills: " + e.getMessage(), e);
        }
        if (count == 0) {
            System.err.println("No skill was deleted. Directory: " + target);
            return;
        }
        System.out.println("Deleted " + count + " Fusion skill(s) from: " + target.toAbsolutePath());
    }

    private Path defaultTarget(final String agent, final String scope) {
        final var userHome = Path.of(System.getProperty("user.home", "."));
        final var isGlobal = scope.equalsIgnoreCase("global");
        switch (agent) {
            case "opencode":
                return isGlobal
                        ? userHome.resolve(".config/opencode/skills")
                        : Path.of("").toAbsolutePath().resolve(".opencode/skills");
            case "claude":
                return isGlobal
                        ? userHome.resolve(".claude/skills")
                        : Path.of("").toAbsolutePath().resolve(".claude/skills");
            case "generic":
                return isGlobal
                        ? userHome.resolve(".agents/skills")
                        : Path.of("").toAbsolutePath().resolve(".agents/skills");
            default:
                throw new IllegalArgumentException("Missing or unknown agent '" + agent + "', provide --agent or --target.");
        }
    }

    @RootConfiguration("-")
    public record Conf(
            @Property(required = true, documentation = "Target coding agent: 'opencode', 'claude' or 'generic'."
                    + "Provide --target to override the resolved path.")
                    String agent,
            @Property(defaultValue = "\"local\"", documentation = "Install scope: 'local' (current project directory) or 'global' (user home).")
                    String scope,
            @Property(documentation = "Explicit target directory holding the skills (overrides the --agent/--scope default).")
                    String target) {
    }
}