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
package io.yupiik.fusion.framework.processor.test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/** Test access to internals hidden by PreparePackaging, without exposing them on the compile classpath. */
public final class ProcessorInternals extends ClassLoader {
    static {
        registerAsParallelCapable();
    }

    public ProcessorInternals(final ClassLoader parent) {
        super(parent);
    }

    public static Path stateDirectory(final Path root, final Path output, final String module) throws IOException {
        final var expected = "fusion-output-state-v1\n" + output.toRealPath().toUri() + "\n" + module + "\n";
        try (final var files = Files.list(root.resolve("outputs-v1"))) {
            for (final var candidate : files.toList()) {
                final var owner = candidate.resolve("owner");
                if (Files.exists(owner) && expected.equals(Files.readString(owner))) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("No owned state for " + output + " (" + module + ")");
    }

    @Override
    protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            var type = findLoadedClass(name);
            if (type == null && name.startsWith("io.yupiik.fusion.framework.processor.internal.")) {
                try (final var stream = getParent().getResourceAsStream(
                        "fusion/annotationprocessor/precompiled/" + name.replace('.', '/') + ".classF")) {
                    if (stream != null) {
                        final var bytes = stream.readAllBytes();
                        type = defineClass(name, bytes, 0, bytes.length);
                    }
                } catch (final IOException e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
            if (type == null) {
                return super.loadClass(name, resolve);
            }
            if (resolve) {
                resolveClass(type);
            }
            return type;
        }
    }

    public static Object invoke(final Method method, final Object instance, final Object... arguments) throws Exception {
        try {
            return method.invoke(instance, arguments);
        } catch (final InvocationTargetException e) {
            if (e.getCause() instanceof Exception exception) {
                throw exception;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }
}
