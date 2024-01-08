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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class CompilationClassLoader extends URLClassLoader implements AutoCloseable {
    private final Thread thread;
    private final ClassLoader oldLoader;

    public CompilationClassLoader(final Path output) throws MalformedURLException {
        super(new URL[]{output.toUri().toURL()}, getSystemClassLoader());

        thread = Thread.currentThread();
        oldLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(this);
    }

    @Override
    public void close() {
        try {
            super.close();
        } catch (final IOException e) {
            // no-op
        }
        thread.setContextClassLoader(oldLoader);
    }
}
