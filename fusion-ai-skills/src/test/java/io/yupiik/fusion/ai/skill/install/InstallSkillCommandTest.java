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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallSkillCommandTest {
    @TempDir
    Path tmp;

    @Test
    void installsIntoGivenTarget() throws Exception {
        final var target = tmp.resolve("custom/skills");
        new InstallSkillCommand(new InstallSkillCommand.Conf(
                "opencode", "local", target.toString(), false)).run();

        assertTrue(Files.isRegularFile(target.resolve("fusion-project-setup/SKILL.md")));
        assertTrue(Files.isRegularFile(target.resolve("fusion-annotation-processing/SKILL.md")));
        assertTrue(Files.isRegularFile(target.resolve("fusion-cli/SKILL.md")));
    }

    @Test
    void skipsExistingWithoutForce() throws Exception {
        final var target = tmp.resolve("opencode");
        new InstallSkillCommand(new InstallSkillCommand.Conf("opencode", "local", target.toString(), false)).run();
        final var skill = target.resolve("fusion-project-setup/SKILL.md");
        final var original = Files.readString(skill);

        new InstallSkillCommand(new InstallSkillCommand.Conf("opencode", "local", target.toString(), false)).run();
        assertEquals(original, Files.readString(skill), "Without --force an existing skill must be left untouched");
    }

    @Test
    void overwritesWithForce() throws Exception {
        final var target = tmp.resolve("opencode");
        final var skill = target.resolve("fusion-project-setup/SKILL.md");
        Files.createDirectories(skill.getParent());
        Files.writeString(skill, "junk");
        new InstallSkillCommand(new InstallSkillCommand.Conf("opencode", "local", target.toString(), true)).run();
        assertNotEquals("junk", Files.readString(skill), "--force must overwrite the existing skill");
    }

    @Test
    void bundledSkillsAreReadWithoutTempExtraction() throws Exception {
        final var tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
        final long before;
        try (final var listing = Files.list(tmpDir)) {
            before = listing.filter(p -> p.getFileName().toString().startsWith("fusion-ai-skills")).count();
        }
        final var skills = Skills.bundled();
        assertFalse(skills.isEmpty(), "Bundled skills must be discoverable from the classpath");
        final long after;
        try (final var listing = Files.list(tmpDir)) {
            after = listing.filter(p -> p.getFileName().toString().startsWith("fusion-ai-skills")).count();
        }
        assertEquals(before, after, "Reading bundled skills must not create temp directories");
    }
}