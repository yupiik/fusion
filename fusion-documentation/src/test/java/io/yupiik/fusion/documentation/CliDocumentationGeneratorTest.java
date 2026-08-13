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
package io.yupiik.fusion.documentation;

import io.yupiik.fusion.cli.internal.CliCommand;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliDocumentationGeneratorTest {
    @Test
    void normalPrefix() {
        final var gen = new CliDocumentationGenerator(Path.of("."), Map.of());
        final var result = gen.generateDetail("my-app",
                new CliDocumentationGenerator.Command("my-command", new String[]{"my-command"}, "A test command.",
                        List.of(new CliCommand.Parameter("my-command.name", "--my-command-name", "The name option."))),
                "index.html").toString();

        assertTrue(result.contains("--name"), "Should show stripped --name, got:\n" + result);
        assertTrue(result.contains("The name option."), "Should include description");
    }

    @Test
    void dashPrefix() { // @RootConfiguration("-") commands have plain config names and "--" cli names
        final var gen = new CliDocumentationGenerator(Path.of("."), Map.of());
        final var result = gen.generateDetail("my-app",
                new CliDocumentationGenerator.Command("other", new String[]{"other"}, "Another test command.",
                        List.of(new CliCommand.Parameter("name", "--name", "The name option."))),
                "index.html").toString();

        assertTrue(result.contains("    --name ..."), "Synopsis should show --name, got:\n" + result);
        assertTrue(result.contains("--name::\nThe name option."), "Parameters should show --name, got:\n" + result);
        assertFalse(result.contains("--other-name"), "Should not show a command prefixed form, got:\n" + result);
        assertFalse(result.contains("-."), "Should not leak the old '-.' key form, got:\n" + result);
    }
}
