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
import java.util.Locale;
import java.util.Optional;

@Command(name = "install", description = "Install the bundled Fusion AI skills into a coding agent skill directory.")
public class InstallSkillCommand implements Runnable {
    private final Conf conf;

    public InstallSkillCommand(final Conf conf) {
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
        var count = 0;
        for (final var skill : Skills.bundled()) {
            final var targetDir = target.resolve(skill.name());
            if (!conf.force() && Files.exists(targetDir.resolve("SKILL.md"))) {
                System.err.println("Skill already installed: " + skill.name()
                        + " in " + targetDir + " (use --force to overwrite).");
                continue;
            }
            try {
                Files.createDirectories(targetDir);
                Files.writeString(targetDir.resolve("SKILL.md"), skill.content());
                System.out.println(" - " + skill.name() + " -> " + targetDir);
                count++;
            } catch (final IOException e) {
                throw new IllegalStateException("Cannot install skill '" + skill.name() + "': " + e.getMessage(), e);
            }
        }
        if (count == 0) {
            System.err.println("No skill was installed. Directory: " + target);
            return;
        }
        System.out.println("Installed " + count + " Fusion skill(s) into: " + target.toAbsolutePath());
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
                    + "Each agent uses its own skill directory layout."
                    + " Defaults: opencode global ~/.config/opencode/skills (Linux/macOS) and %USERPROFILE%\\.config\\opencode\\skills (Windows),"
                    + " local .opencode/skills; claude global ~/.claude/skills (Linux/macOS) and %USERPROFILE%\\.claude\\skills (Windows),"
                    + " local .claude/skills; generic global ~/.agents/skills and local .agents/skills."
                    + " Provide --target to override the resolved path.")
                    String agent,
            @Property(defaultValue = "\"local\"", documentation = "Install scope: 'local' (current project directory) or 'global' (user home).")
                    String scope,
            @Property(documentation = "Explicit target directory holding the skills (overrides the --agent/--scope default)."
                    + "Common values: opencode global is ~/.config/opencode/skills on Linux/macOS and %USERPROFILE%\\.config\\opencode\\skills on Windows;"
                    + " claude global is ~/.claude/skills on Linux/macOS and %USERPROFILE%\\.claude\\skills on Windows."
                    + " Per-project installs use .opencode/skills and .claude/skills relative to the project root.")
                    String target,
            @Property(defaultValue = "false", documentation = "Overwrite already installed skills instead of skipping them.")
                    boolean force) {
    }
}