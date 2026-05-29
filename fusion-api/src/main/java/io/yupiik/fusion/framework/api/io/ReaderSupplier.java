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
package io.yupiik.fusion.framework.api.io;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

@FunctionalInterface
public interface ReaderSupplier {
    Reader get() throws IOException;

    /**
     * Loads the content from classpath resource.
     * @param resource resource path.
     * @return the reader supplier.
     */
    public static ReaderSupplier fromClasspath(final String resource) {
        return () -> new InputStreamReader(
                requireNonNull(Thread.currentThread().getContextClassLoader().getResourceAsStream(resource), resource),
                UTF_8);
    }

    /**
     * Loads the content from a file.
     * @param resource file path.
     * @return the reader supplier.
     */
    public static ReaderSupplier fromFile(final Path resource) {
        return () -> Files.newBufferedReader(resource);
    }

    /**
     * Loads the content from an in memory String.
     * @param content inline content to use.
     * @return the reader supplier.
     */
    public static ReaderSupplier fromInline(final String content) {
        return () -> new StringReader(content);
    }

    /**
     * Assume the resource is a file path and if not uses a classpath resource.
     * @param resource path to load.
     * @param defaultValue value if the resource is missing or not readable, if null the errors are passthrough.
     * @return the reader supplier.
     */
    public static ReaderSupplier from(final String resource, final String defaultValue) {
        return () -> {
            ReaderSupplier delegate;
            try {
                final var path = Path.of(resource);
                delegate = (Files.exists(path) ? fromFile(path) : fromClasspath(resource));
            } catch (final RuntimeException re) {
                delegate = fromClasspath(resource);
            }
            try {
                return delegate.get();
            } catch (final IOException | RuntimeException | Error e) {
                if (defaultValue == null) {
                    throw e;
                }
                return new java.io.StringReader(defaultValue);
            }
        };
    }
}