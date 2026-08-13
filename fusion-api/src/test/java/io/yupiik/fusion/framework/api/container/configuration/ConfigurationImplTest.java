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
package io.yupiik.fusion.framework.api.container.configuration;

import io.yupiik.fusion.framework.api.configuration.ConfigurationSource;
import io.yupiik.fusion.framework.api.event.Emitter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConfigurationImplTest {
    // resetSecretsProperty() runs for every test of this class, including tests that never set the
    // property (noPrefixRootConfigurationFallback, addSource). Only clear when this test instance
    // actually set it, otherwise an unlocked teardown would clear the property of a concurrently
    // running locked test (the @ResourceLock only serializes the annotated test methods).
    private boolean secretDirectoryConfigured;

    @AfterEach
    void resetSecretsProperty() {
        if (secretDirectoryConfigured) {
            System.clearProperty("fusion.configuration.sources.secrets");
            secretDirectoryConfigured = false;
        }
    }

    @Test
    @ResourceLock(value = "fusion.configuration.sources.secrets", mode = ResourceAccessMode.READ_WRITE)
    void secretsDefaults(@TempDir final Path work) throws IOException {
        System.setProperty("fusion.configuration.sources.secrets", work.toString());
        secretDirectoryConfigured = true;
        Files.writeString(work.resolve("test"), "some");
        final var configuration = new ConfigurationImpl(List.of());
        assertEquals("some", configuration.get("test").orElse(null));
    }

    @Test
    @ResourceLock(value = "fusion.configuration.sources.secrets", mode = ResourceAccessMode.READ_WRITE)
    void secretsStrip(@TempDir final Path work) throws IOException {
        System.setProperty("fusion.configuration.sources.secrets", work.toString());
        secretDirectoryConfigured = true;
        Files.writeString(work.resolve("_fusion.secrets.configuration.properties"), "folder.name.mode=strip");
        Files.writeString(work.resolve(work.getFileName() + ".test"), "some");
        final var configuration = new ConfigurationImpl(List.of());
        assertEquals("some", configuration.get("test").orElse(null));
    }

    @Test
    @ResourceLock(value = "fusion.configuration.sources.secrets", mode = ResourceAccessMode.READ_WRITE)
    void secretsConcat(@TempDir final Path work) throws IOException {
        System.setProperty("fusion.configuration.sources.secrets", work.toString());
        secretDirectoryConfigured = true;
        Files.writeString(work.resolve("test"), "some");
        Files.writeString(work.resolve("_fusion.secrets.configuration.properties"), "folder.name.mode=concat");
        final var configuration = new ConfigurationImpl(List.of());
        assertEquals("some", configuration.get(work.getFileName() + ".test").orElse(null));
    }

    @Test
    void noPrefixRootConfigurationFallback() {
        final var configuration = new ConfigurationImpl(List.of(new ConfigurationSource() {
            @Override
            public String get(final String key) {
                return switch (key) {
                    case "port" -> "8080";
                    case "-.explicit" -> "direct";
                    case "explicit" -> "should not win";
                    default -> null;
                };
            }
        }));
        // old generated factories ("-" root configurations) read `-.x` keys, plain keys must match too
        assertEquals("8080", configuration.get("-.port").orElseThrow());
        // but an explicitly prefixed key wins
        assertEquals("direct", configuration.get("-.explicit").orElseThrow());
    }

    @Test
    void addSource() throws IOException {
        final var matchedKey1 = "ConfigurationImplTest.addSource1";
        final var matchedKey2 = "ConfigurationImplTest.addSource2";
        final var configuration = new ConfigurationImpl(List.of(new ConfigurationSource() {
            @Override
            public String get(final String key) {
                return key.equals(matchedKey1) ? "!" : null;
            }
        }), new Emitter() {
            @Override
            public <T> void emit(final T event) {
                if (event instanceof ConfigurationRegistration registration) {
                    registration.addSource().accept(new ConfigurationSource() {
                        @Override
                        public String get(final String key) {
                            return key.equals(matchedKey2) ? "done" + registration.configuration().get(matchedKey1).orElseThrow() : null;
                        }
                    });
                }
            }
        });
        assertEquals("done!", configuration.get(matchedKey2).orElse(null));
    }
}
