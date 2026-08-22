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

import io.yupiik.fusion.framework.api.configuration.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.parallel.Resources.SYSTEM_OUT;

@ResourceLock(SYSTEM_OUT)
class CompletionBashTest {
    private final CompletionBashCommand command = new CompletionBashCommand();
    private final List<CliCommand<? extends Runnable>> commands = CliCommandFixture.commands();

    @Test
    void topLevelCandidates() {
        assertEquals(List.of("completion", "discover", "init", "scan"),
                command.candidatesFor("", 0, commands));
        assertEquals(List.of("scan"), command.candidatesFor("s", 1, commands));
    }

    @Test
    void subCommandCandidates() {
        assertEquals(List.of("all", "company", "fill"),
                command.candidatesFor("scan ", 5, commands));
        assertEquals(List.of("all"), command.candidatesFor("scan a", "scan a".length(), commands));
    }

    @Test
    void optionCandidatesAfterRootCommand() {
        assertEquals(List.of("--dry-run", "--limit"),
                command.candidatesFor("scan all ", "scan all ".length(), commands));
        assertEquals(List.of("--dry-run"),
                command.candidatesFor("scan all --d", "scan all --d".length(), commands));
    }

    @Test
    void scriptMentionsCommandsAndOptions() {
        final var script = command.bashScript(commands, "app");
        assertTrue(script.contains("complete -F _app_complete app"));
        assertTrue(script.contains("scan all"));
        assertTrue(script.contains("--dry-run"));
        assertTrue(script.contains("_app_complete_cmds"));
    }

    @Test
    void descriptionsExplainUsage() {
        assertTrue(new CompletionBashCommand().description().contains("Usage"));
        assertTrue(new CompletionZshCommand().description().contains("Usage"));
        assertTrue(new CompletionPowerShellCommand().description().contains("Usage"));
    }

    @Test
    void writeRoutesToCandidatesWhenCompKeysAreSet() {
        final var output = runWrite(Map.of("comp.line", "scan all --d", "comp.point", "12"));
        assertTrue(output.contains("--dry-run"), output);
        assertTrue(!output.contains("complete -F"), output);
    }

    @Test
    void writeRoutesToScriptWithoutCompKeys() {
        final var output = capture(() -> command.write(commands, configuration(Map.of())));
        assertTrue(output.contains("complete -F "), output);
    }

    private String runWrite(final Map<String, String> values) {
        return capture(() -> command.write(commands, configuration(values)));
    }

    private static Configuration configuration(final Map<String, String> values) {
        return Configuration.of(values);
    }

    private static String capture(final Runnable runnable) {
        final var previous = System.out;
        final var out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true));
            runnable.run();
            return out.toString();
        } finally {
            System.setOut(previous);
        }
    }
}