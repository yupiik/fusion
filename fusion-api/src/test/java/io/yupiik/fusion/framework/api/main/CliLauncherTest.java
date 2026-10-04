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
package io.yupiik.fusion.framework.api.main;

import io.yupiik.fusion.framework.api.configuration.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CliLauncherTest {
    @Test
    void multiSegmentCommandDoesNotPolluteConfiguration() {
        try (final var launcher = new CliLauncher("client", "list", "--tracker-cli-url", "http://x")) {
            final var configuration = launcher.container.lookup(Configuration.class).instance();
            assertEquals("http://x", configuration.get("tracker.cli.url").orElse(null));
            assertEquals("http://x", configuration.get("tracker-cli-url").orElse(null));
            assertNull(configuration.get("client").orElse(null));
            assertNull(configuration.get("list").orElse(null));
        }
    }

    @Test
    void valuelessOptionIsRejected() {
        try (final var launcher = new CliLauncher("client", "list", "--raw-json")) {
            // the source bean is created when Configuration is instantiated
            final var error = assertThrows(IllegalArgumentException.class,
                    () -> launcher.container.lookup(io.yupiik.fusion.framework.api.configuration.Configuration.class).instance());
            assertEquals("Option '--raw-json' requires a value (use '--raw-json=value')", error.getMessage());
        }
    }

    @Test
    void helpEndToEndWorks() {
        try (final var launcher = new CliLauncher("client", "list", "--help", "--tracker-cli-url", "http://x")) {
            final var configuration = launcher.container.lookup(Configuration.class).instance();
            assertNull(configuration.get("help").orElse(null)); // --help is skipped, not a config entry
            assertEquals("http://x", configuration.get("tracker-cli-url").orElse(null));
        }
    }

    @Test
    void negatedOptionExposesBothKeys() {
        try (final var launcher = new CliLauncher("client", "list", "--no-raw-json")) {
            final var configuration = launcher.container.lookup(Configuration.class).instance();
            assertEquals("false", configuration.get("raw-json").orElse(null));
            assertEquals("false", configuration.get("no.raw.json").orElse(null)); // dot alias of no-raw-json
        }
    }
}