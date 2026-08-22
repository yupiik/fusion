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

import io.yupiik.fusion.cli.CliAwaiter;
import io.yupiik.fusion.framework.api.container.ConfiguringContainerImpl;
import io.yupiik.fusion.framework.api.container.bean.ProvidedInstanceBean;
import io.yupiik.fusion.framework.api.main.Args;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.parallel.Resources.SYSTEM_OUT;
import static org.junit.jupiter.api.parallel.Resources.SYSTEM_PROPERTIES;

@ResourceLock(SYSTEM_OUT)
@ResourceLock(SYSTEM_PROPERTIES)
class CompletionCliTest {
    private String run(final String shell, final List<String> args) {
        final var previous = System.getProperty("fusion.cli.shell");
        System.setProperty("fusion.cli.shell", shell);
        final var out = new ByteArrayOutputStream();
        final var previousOut = System.out;
        try (final var container = new ConfiguringContainerImpl()
                .register(new ProvidedInstanceBean<>(DefaultScoped.class, Args.class, () -> new Args(args)))
                .start()) {
            System.setOut(new PrintStream(out, true));
            container.lookup(CliAwaiter.class).instance().await();
            return out.toString();
        } finally {
            System.setOut(previousOut);
            if (previous == null) {
                System.clearProperty("fusion.cli.shell");
            } else {
                System.setProperty("fusion.cli.shell", previous);
            }
        }
    }

    @Test
    void generatesBashScript() {
        final var output = run("bash", List.of("completion", "bash"));
        assertTrue(output.contains("_app_complete()"));
        assertTrue(output.contains("complete -F _app_complete app"));
        assertTrue(output.contains("_app_complete_cmds"));
    }

    @Test
    void generatesZshScript() {
        final var output = run("zsh", List.of("completion", "zsh"));
        assertTrue(output.startsWith("#compdef app"));
        assertTrue(output.contains("compdef _app app"));
    }

    @Test
    void generatesPowerShellScript() {
        final var output = run("powershell", List.of("completion", "powershell"));
        assertTrue(output.contains("Register-ArgumentCompleter -Native -CommandName 'app'"));
    }

    @Test
    void commandIsRegisteredAsACliCommand() {
        final var output = run("bash", List.of("completion", "bash"));
        // the completion command itself is registered, so it appears in its own command set
        assertTrue(output.contains("completion bash"));
    }
}