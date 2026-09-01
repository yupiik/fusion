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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeleteSkillCommandTest {
    @TempDir
    Path tmp;

    @Test
    void deletesInstalledSkills() {
        final var target = tmp.resolve("opencode");
        new InstallSkillCommand(new InstallSkillCommand.Conf("opencode", "local", target.toString(), false)).run();
        assertTrue(Files.isRegularFile(target.resolve("fusion-cli/SKILL.md")));

        new DeleteSkillCommand(new DeleteSkillCommand.Conf("opencode", "local", target.toString())).run();
        assertFalse(Files.exists(target.resolve("fusion-cli")));
        assertFalse(Files.exists(target.resolve("fusion-project-setup")));
    }

    @Test
    void nothingToDeleteIsNotAnError() {
        final var target = tmp.resolve("missing");
        new DeleteSkillCommand(new DeleteSkillCommand.Conf("opencode", "local", target.toString())).run();
        assertFalse(Files.exists(target));
    }
}