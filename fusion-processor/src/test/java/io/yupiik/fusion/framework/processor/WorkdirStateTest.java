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
package io.yupiik.fusion.framework.processor;

import io.yupiik.fusion.framework.processor.test.ProcessorInternals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkdirStateTest {
    @TempDir
    Path work;

    @Test
    void isolatesOutputsAndJpmsIdentitiesAndReleasesOwnership() throws Exception {
        final var type = stateType();
        Path mainDirectory;
        try (final var main = open(type, "state", "classes", "");
             final var tests = open(type, "state", "test-classes", "");
             final var named = open(type, "state", "classes", "app.module")) {
            mainDirectory = directory(type, main);
            assertNotEquals(mainDirectory, directory(type, tests));
            assertNotEquals(mainDirectory, directory(type, named));
            Files.writeString(mainDirectory.resolve("retained"), "main state");
        }
        try (final var main = open(type, "state", "classes", "")) {
            assertEquals(mainDirectory, directory(type, main));
            assertEquals("main state", Files.readString(mainDirectory.resolve("retained")));
        }
    }

    @Test
    void rejectsConcurrentCompilersWithoutTouchingRetainedState() throws Exception {
        final var type = stateType();
        try (final var main = open(type, "state", "classes", "")) {
            final var retained = directory(type, main).resolve("retained");
            Files.writeString(retained, "unchanged");
            final var failure = assertThrows(IllegalStateException.class, () -> open(type, "state", "classes", ""));
            assertTrue(failure.getMessage().contains("Concurrent compilation"), failure.getMessage());
            assertEquals("unchanged", Files.readString(retained));
        }
        try (final var main = open(type, "state", "classes", "")) {
            assertEquals("unchanged", Files.readString(directory(type, main).resolve("retained")));
        }
    }

    @Test
    void rejectsAmbiguousLegacyStateEvenAlongsidePartitions() throws Exception {
        final var type = stateType();
        try (final var state = open(type, "state", "classes", "")) {
            assertTrue(Files.exists(directory(type, state).resolve("owner")));
        }
        Files.writeString(work.resolve("state/compact-v1.bin"), "old unowned state");
        final var failure = assertThrows(IllegalStateException.class, () -> open(type, "state", "test-classes", ""));
        assertTrue(failure.getMessage().contains("full clean rebuild"), failure.getMessage());
        assertEquals("old unowned state", Files.readString(work.resolve("state/compact-v1.bin")));
    }

    @Test
    void incompatibleOrMissingOwnerCannotBeSilentlyAdopted() throws Exception {
        final var type = stateType();
        final Path directory;
        try (final var state = open(type, "state", "classes", "")) {
            directory = directory(type, state);
            Files.writeString(directory.resolve("compact-v1.bin"), "retained");
        }
        Files.writeString(directory.resolve("owner"), "another format or owner");
        assertTrue(assertThrows(IllegalStateException.class, () -> open(type, "state", "classes", ""))
                .getMessage().contains("Incompatible"));
        Files.delete(directory.resolve("owner"));
        assertTrue(assertThrows(IllegalStateException.class, () -> open(type, "state", "classes", ""))
                .getMessage().contains("Missing"));
        assertEquals("retained", Files.readString(directory.resolve("compact-v1.bin")));
    }

    @Test
    void refusesUnverifiableOutputAndPackagedWorkState() throws Exception {
        final var type = stateType();
        assertTrue(assertThrows(IllegalStateException.class, () -> open(type, "state", null, ""))
                .getMessage().contains("verifiable file class output"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> open(type, "classes/cache", "classes", ""))
                .getMessage().contains("outside packaged class output"));
    }

    private Class<?> stateType() throws Exception {
        return new ProcessorInternals(getClass().getClassLoader())
                .loadClass("io.yupiik.fusion.framework.processor.internal.WorkdirState");
    }

    private AutoCloseable open(final Class<?> type, final String root, final String output, final String module) throws Exception {
        return (AutoCloseable) ProcessorInternals.invoke(type.getMethod("open", Path.class, Path.class, String.class), null,
                work.resolve(root), output == null ? null : work.resolve(output), module);
    }

    private Path directory(final Class<?> type, final AutoCloseable state) throws Exception {
        return (Path) ProcessorInternals.invoke(type.getMethod("directory"), state);
    }
}
