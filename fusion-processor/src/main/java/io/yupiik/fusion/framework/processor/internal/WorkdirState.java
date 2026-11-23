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
package io.yupiik.fusion.framework.processor.internal;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

/** Explicit work roots are containers, not shared mutable fragment inventories. */
public final class WorkdirState implements AutoCloseable {
    private final Path directory;
    private final FileChannel channel;
    private final FileLock lock;

    private WorkdirState(final Path directory, final FileChannel channel, final FileLock lock) {
        this.directory = directory;
        this.channel = channel;
        this.lock = lock;
    }

    public static WorkdirState open(final Path root, final Path output, final String module) throws IOException {
        if (output == null) {
            throw new IllegalStateException("Explicit fusion.workdir requires a verifiable file class output; disable it with false or perform a full clean rebuild with a supported output");
        }
        Files.createDirectories(output);
        final var realOutput = output.toRealPath();
        Files.createDirectories(root);
        final var realRoot = root.toRealPath();
        if (realRoot.startsWith(realOutput)) {
            throw new IllegalArgumentException("fusion.workdir must be outside packaged class output");
        }
        try (final var files = Files.list(realRoot)) {
            if (files.anyMatch(it -> !"outputs-v1".equals(it.getFileName().toString()))) {
                throw new IllegalStateException("Legacy or incompatible fusion.workdir state; perform a full clean rebuild of all outputs sharing this work root and remove the old work state");
            }
        }
        // Physical identity is appropriate for state ownership ONLY, never for generated Java names.
        final var owner = "fusion-output-state-v1\n" + realOutput.toUri() + "\n" + module + "\n";
        final String key;
        try {
            key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(owner.getBytes(UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        final var directory = realRoot.resolve("outputs-v1").resolve(key);
        Files.createDirectories(directory);
        final var channel = FileChannel.open(directory.resolve("state.lock"), CREATE, WRITE);
        FileLock lock = null;
        try {
            try {
                lock = channel.tryLock();
            } catch (final OverlappingFileLockException concurrent) {
                throw new IllegalStateException("Concurrent compilation sharing fusion.workdir and class output is not supported", concurrent);
            }
            if (lock == null) {
                throw new IllegalStateException("Concurrent compilation sharing fusion.workdir and class output is not supported");
            }
            final var ownership = directory.resolve("owner");
            if (Files.exists(ownership)) {
                if (!owner.equals(Files.readString(ownership, UTF_8))) {
                    throw new IllegalStateException("Incompatible fusion.workdir ownership; perform a full clean rebuild");
                }
            } else {
                try (final var files = Files.list(directory)) {
                    if (files.anyMatch(it -> !"state.lock".equals(it.getFileName().toString()))) {
                        throw new IllegalStateException("Missing fusion.workdir ownership; perform a full clean rebuild");
                    }
                }
                Files.writeString(ownership, owner, UTF_8);
            }
            return new WorkdirState(directory, channel, lock);
        } catch (final IOException | RuntimeException e) {
            if (lock != null) {
                lock.release();
            }
            channel.close();
            throw e;
        }
    }

    public Path directory() {
        return directory;
    }

    @Override
    public void close() throws IOException {
        try {
            lock.release();
        } finally {
            channel.close();
        }
    }
}
