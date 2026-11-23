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

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.tools.ToolProvider;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedFactoriesTest {
    @TempDir
    Path work;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void discoversRealOwnersAndIgnoresRootOverflowDuplicates(final boolean packaged) throws Exception {
        final var sources = work.resolve("sources/demo");
        final var classes = work.resolve("classes");
        Files.createDirectories(sources);
        Files.createDirectories(classes);
        final var module = sources.resolve("Module.java");
        Files.writeString(module, """
                package demo;
                public class Module implements io.yupiik.fusion.framework.api.container.FusionModule {
                    public static String Local() { return "local"; }
                    public static String Overflow() { return Owner.Overflow(); }
                }
                """);
        final var owner = sources.resolve("Owner.java");
        Files.writeString(owner, """
                package demo;
                public class Owner {
                    public static String Overflow() { return "overflow"; }
                    private static String PrivateHelper() { return "private"; }
                    public String Instance() { return "instance"; }
                    public static String WithArgument(String value) { return value; }
                }
                """);
        final var compiler = ToolProvider.getSystemJavaCompiler();
        final var output = new StringWriter();
        try (final var manager = compiler.getStandardFileManager(null, null, null)) {
            assertTrue(compiler.getTask(output, manager, null,
                    List.of("-proc:none", "--release", "17", "-classpath", System.getProperty("java.class.path"),
                            "-d", classes.toString()), null,
                    manager.getJavaFileObjectsFromPaths(List.of(module, owner))).call(), output.toString());
        }
        final Path archive;
        if (packaged) {
            archive = work.resolve("fixture.jar");
            try (final var jar = new JarOutputStream(Files.newOutputStream(archive));
                 final var files = Files.walk(classes)) {
                for (final var file : files.filter(Files::isRegularFile).toList()) {
                    jar.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                    Files.copy(file, jar);
                    jar.closeEntry();
                }
            }
        } else {
            archive = classes;
        }
        try (final var loader = new URLClassLoader(new URL[]{archive.toUri().toURL()}, getClass().getClassLoader())) {
            final var methods = GeneratedFactories.methods(loader, "demo", String.class);
            assertEquals(List.of("Local", "Overflow"), methods.stream().map(it -> it.getName()).toList());
            assertEquals("demo.Owner", methods.get(1).getDeclaringClass().getName());
            assertEquals("local", GeneratedFactories.invoke(loader, "demo", "Local", String.class));
            assertEquals("overflow", GeneratedFactories.invoke(loader, "demo", "Overflow", String.class));
        }
    }
}
