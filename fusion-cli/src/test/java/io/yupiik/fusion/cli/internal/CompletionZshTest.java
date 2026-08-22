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

import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletionZshTest {
    @Test
    void generatesCompdef() {
        final var script = new CompletionZshCommand().zshScript(CliCommandFixture.commands(), "app");
        assertTrue(script.startsWith("#compdef app"));
        assertTrue(script.contains("completion bash"));
        assertTrue(script.contains("scan all"));
        assertTrue(script.contains("compdef _app app"));
    }
}