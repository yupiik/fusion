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
package io.yupiik.fusion.build.internal.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentsFileSynchronizerTest {
    @Test
    void synchronize(@TempDir final Path output) throws IOException {
        new AgentsFileSynchronizer(repositoryRoot(), output, version(), false).run();

        final var root = output.resolve("AGENTS.md");
        assertTrue(Files.exists(root), "missing root AGENTS.md");
        final var rootContent = Files.readString(root);
        assertTrue(rootContent.contains("GENERATED FILE"), "missing banner");
        assertTrue(rootContent.contains("fusion-json"), "missing module map");
        assertTrue(rootContent.contains("mvn install"), "missing build commands");

        assertTrue(Files.readString(output.resolve("CLAUDE.md")).contains("@AGENTS.md"), "missing AGENTS.md import");
        assertTrue(Files.exists(output.resolve("fusion-api/AGENTS.md")), "missing module AGENTS.md");
        assertTrue(Files.exists(output.resolve("fusion-httpclient-parent/fusion-httpclient/AGENTS.md")), "missing nested module AGENTS.md");
        assertTrue(
                Files.readString(output.resolve("fusion-build-api/AGENTS.md")).contains("`@RootConfiguration`"),
                "missing annotation catalog");
        assertTrue(
                Files.readString(output.resolve("fusion-jwt/AGENTS.md")).contains("| Name | Env variable |"),
                "missing configuration table");

        // rerunning does not modify anything (idempotent)
        final var content = Files.readString(output.resolve("fusion-jwt/AGENTS.md"));
        new AgentsFileSynchronizer(repositoryRoot(), output, version(), false).run();
        assertEquals(content, Files.readString(output.resolve("fusion-jwt/AGENTS.md")));
    }

    @Test
    void checkModeDetectsStaleFiles(@TempDir final Path output) throws IOException {
        final var synchronizer = new AgentsFileSynchronizer(repositoryRoot(), output, version(), false);
        synchronizer.run();

        final var check = new AgentsFileSynchronizer(repositoryRoot(), output, version(), true);
        assertDoesNotThrow(check::run);

        Files.writeString(output.resolve("AGENTS.md"), "stale");
        final var error = assertThrows(IllegalStateException.class, check::run);
        assertTrue(error.getMessage().contains("AGENTS.md"), error::getMessage);
    }

    private Path repositoryRoot() {
        return Path.of(System.getProperty("fusion.build-internal.repositoryRoot", ".."))
                .toAbsolutePath()
                .normalize();
    }

    private String version() {
        return System.getProperty("project.version", "unknown");
    }
}
