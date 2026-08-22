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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CliShellTest {
    @Test
    void allRegistersEveryShell() {
        assertEquals(List.of(CliShell.BASH, CliShell.ZSH, CliShell.POWERSHELL),
                CliShell.select("all", "/bin/bash", false));
    }

    @Test
    void noneRegistersNothing() {
        assertEquals(List.of(), CliShell.select("none", "/bin/bash", false));
    }

    @Test
    void explicitShellWinsOverEnvironment() {
        assertEquals(List.of(CliShell.ZSH), CliShell.select("zsh", "/bin/bash", false));
        assertEquals(List.of(CliShell.POWERSHELL), CliShell.select("powershell", "/bin/bash", false));
        assertEquals(List.of(CliShell.BASH), CliShell.select("bash", "/usr/bin/zsh", true));
    }

    @Test
    void explicitValueIsCaseInsensitiveAndTrimmed() {
        assertEquals(List.of(CliShell.POWERSHELL), CliShell.select("  POWERSHELL ", "/bin/bash", false));
    }

    @Test
    void detectsZshFromShell() {
        assertEquals(List.of(CliShell.ZSH), CliShell.select("", "/usr/bin/zsh", false));
    }

    @Test
    void detectsBashFromShell() {
        assertEquals(List.of(CliShell.BASH), CliShell.select("", "/bin/bash", false));
    }

    @Test
    void defaultsToPowerShellOnWindows() {
        assertEquals(List.of(CliShell.POWERSHELL), CliShell.select("", "", true));
    }

    @Test
    void defaultsToBashWhenInconclusive() {
        assertEquals(List.of(CliShell.BASH), CliShell.select("", "", false));
    }
}