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
    @Test
    @ResourceLock(value = "fusion.configuration.sources.secrets", mode = ResourceAccessMode.READ_WRITE)
    void secretsDefaults(@TempDir final Path work) throws IOException {
        System.setProperty("fusion.configuration.sources.secrets", work.toString());
        Files.writeString(work.resolve("test"), "some");
        final var configuration = new ConfigurationImpl(List.of());
        assertEquals("some", configuration.get("test").orElse(null));
    }

    @Test
    @ResourceLock(value = "fusion.configuration.sources.secrets", mode = ResourceAccessMode.READ_WRITE)
    void secretsStrip(@TempDir final Path work) throws IOException {
        System.setProperty("fusion.configuration.sources.secrets", work.toString());
        Files.writeString(work.resolve("_fusion.secrets.configuration.properties"), "folder.name.mode=strip");
        Files.writeString(work.resolve(work.getFileName() + ".test"), "some");
        final var configuration = new ConfigurationImpl(List.of());
        assertEquals("some", configuration.get("test").orElse(null));
    }

    @Test
    @ResourceLock(value = "fusion.configuration.sources.secrets", mode = ResourceAccessMode.READ_WRITE)
    void secretsConcat(@TempDir final Path work) throws IOException {
        System.setProperty("fusion.configuration.sources.secrets", work.toString());
        Files.writeString(work.resolve("test"), "some");
        Files.writeString(work.resolve("_fusion.secrets.configuration.properties"), "folder.name.mode=concat");
        final var configuration = new ConfigurationImpl(List.of());
        assertEquals("some", configuration.get(work.getFileName() + ".test").orElse(null));
    }
}
