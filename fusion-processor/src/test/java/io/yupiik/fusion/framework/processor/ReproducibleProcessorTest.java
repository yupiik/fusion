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

import io.yupiik.fusion.framework.api.container.FusionModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static io.yupiik.fusion.framework.processor.test.ProcessorInternals.stateDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReproducibleProcessorTest {
    @TempDir
    Path work;

    @Test
    void relocationAndArbitraryLayoutsHaveIdenticalGeneratedSourcesAndClassBytes() throws Exception {
        final var sources = Map.of("demo.A", bean("A", 1), "demo.B", bean("B", 2));
        final var first = layout("job@2/first/target/classes", "job@2/first/generated", "state-one");
        final var second = layout("renamed/arbitrary/out/deep", "elsewhere/source-output", "state-two");
        compile(first, sources, "modules.Main");
        compile(second, sources, "modules.Main");
        assertFalse(holders(first).isEmpty());
        assertEquals(tree(first.generated()), tree(second.generated()));
        assertEquals(tree(first.output()), tree(second.output()));
    }

    @Test
    void digestIncludesRetainedReplacedAndPrunedFragmentsAndRetiresOldNamespaces() throws Exception {
        final var incremental = layout("incremental/classes", "incremental/generated", "incremental/state");
        compile(incremental, Map.of("demo.A", bean("A", 1), "demo.B", bean("B", 2)), "modules.Main");
        final var original = holders(incremental);
        compile(incremental, Map.of("demo.B", bean("B", 3)), "modules.Main");
        final var replaced = holders(incremental);
        assertNotEquals(original, replaced, "Same stable IDs but changed logical binding content must change the digest");
        assertRetired(incremental, original);
        final var clean = layout("clean/not-target", "clean/source-output", "clean/state");
        compile(clean, Map.of("demo.A", bean("A", 1), "demo.B", bean("B", 3)), "modules.Main");
        assertEquals(tree(clean.generated()), tree(incremental.generated()));
        assertEquals(tree(clean.output()), tree(incremental.output()));
        // An untouched retained fragment participates even when the invocation only recompiles A.
        compile(incremental, Map.of("demo.A", bean("A", 1)), "modules.Main");
        assertEquals(tree(clean.generated()), tree(incremental.generated()));
        assertEquals(tree(clean.output()), tree(incremental.output()));
        Files.delete(incremental.sources().resolve("demo/B.java")); // leave B.class deliberately stale
        compile(incremental, Map.of("demo.A", bean("A", 1)), "modules.Main");
        assertRetired(incremental, replaced);
        final var deleted = layout("deleted/out", "deleted/gen", "deleted/state");
        compile(deleted, Map.of("demo.A", bean("A", 1)), "modules.Main");
        assertEquals(tree(deleted.generated()), tree(incremental.generated()));
        assertEquals(List.of("demo.A"), beans(incremental, "modules.Main"));
    }

    @Test
    void sameDirectoryAndPackageButDifferentContentsDoNotShareInternalNames() throws Exception {
        final var first = layout("one/core/target/classes", "one/core/generated", "one/state");
        final var second = layout("two/core/target/classes", "two/core/generated", "two/state");
        compile(first, Map.of("demo.A", bean("A", 1)), "modules.Main");
        compile(second, Map.of("demo.B", bean("B", 1)), "modules.Main");
        assertTrue(java.util.Collections.disjoint(holders(first), holders(second)));
        // The public module FQN still needs to be unique when the jars are used together.
        compile(second, Map.of("demo.B", bean("B", 1)), "modules.Other");
        final var jars = List.of(jar(first, "first.jar"), jar(second, "second.jar"));
        try (final var loader = new URLClassLoader(jars.stream().map(path -> {
            try {
                return path.toUri().toURL();
            } catch (final java.net.MalformedURLException e) {
                throw new IllegalStateException(e);
            }
        }).toArray(URL[]::new), getClass().getClassLoader())) {
            for (final var entry : Map.of("modules.Main", "demo.A", "modules.Other", "demo.B").entrySet()) {
                final var module = (FusionModule) loader.loadClass(entry.getKey()).getConstructor().newInstance();
                assertEquals(List.of(entry.getValue()), module.beans().map(it -> it.type().getTypeName()).toList());
            }
        }
    }

    @Test
    void explicitSharedWorkdirMainABThenTestThenPartialMainA() throws Exception {
        final var main = layout("project/target/classes", "project/target/generated-main", "project/target/fusion");
        final var tests = layout("project/target/test-classes", "project/target/generated-test", "project/target/fusion");
        compile(main, Map.of("demo.A", bean("A", 1), "demo.B", bean("B", 2)), "demo.Module");
        final var original = tree(main.output());
        final var mainState = stateDirectory(main.state(), main.output(), "");
        final var inventory = tree(mainState);
        compile(tests, Map.of("demo.TestBean", bean("TestBean", 3)), "demo.Module");
        assertEquals(List.of("demo.TestBean"), beans(tests, "demo.Module"));
        assertEquals(inventory, tree(mainState), "Test compilation must not prune or rewrite main state");
        assertNotEquals(mainState, stateDirectory(tests.state(), tests.output(), ""));
        compile(main, Map.of("demo.A", bean("A", 1)), "demo.Module");
        assertEquals(List.of("demo.A", "demo.B"), beans(main, "demo.Module"));
        assertEquals(original, tree(main.output()));
        assertEquals(List.of("demo.TestBean"), beans(tests, "demo.Module"));
    }

    private Layout layout(final String output, final String generated, final String state) {
        return new Layout(work.resolve(output), work.resolve(generated), work.resolve(state),
                work.resolve(generated + "-business-sources"));
    }

    @Test
    void unverifiableCompilerOutputCannotAdoptExplicitSharedState() throws Exception {
        assertUnverifiableOutputRejected(false);
    }

    @Test
    void unverifiableNamedModuleOutputCannotSilentlyDisableExplicitState() throws Exception {
        assertUnverifiableOutputRejected(true);
    }

    private void assertUnverifiableOutputRejected(final boolean named) throws Exception {
        final var source = work.resolve("A.java");
        Files.writeString(source, bean("A", 1));
        final var output = Files.createDirectories(work.resolve("classes"));
        final var compiler = ToolProvider.getSystemJavaCompiler();
        try (final var standard = compiler.getStandardFileManager(null, null, null);
             final var manager = new ForwardingJavaFileManager<>(standard) {
                 @Override
                 public FileObject getFileForInput(final JavaFileManager.Location location, final String pack,
                                                   final String name) throws IOException {
                     if ("fusion_marker_not_used_anywhere".equals(name)) {
                         return new SimpleJavaFileObject(URI.create("memory:///" + name), JavaFileObject.Kind.OTHER) {};
                     }
                     return super.getFileForInput(location, pack, name);
                 }

                 @Override
                 public FileObject getFileForOutput(final JavaFileManager.Location location, final String pack,
                                                    final String name, final FileObject sibling) throws IOException {
                     if ("fusion_marker_not_used_anywhere".equals(name)) {
                         return new SimpleJavaFileObject(URI.create("memory:///" + name), JavaFileObject.Kind.OTHER) {};
                     }
                     return super.getFileForOutput(location, pack, name, sibling);
                 }
             }) {
            final var options = new ArrayList<>(List.of("--release", "17",
                    "-classpath", System.getProperty("java.class.path"), "-d", output.toString(),
                    "-Afusion.workdir=" + work.resolve("state")));
            final var sources = new ArrayList<>(List.of(source));
            if (named) {
                final var descriptor = work.resolve("module-info.java");
                Files.writeString(descriptor, "module app.module {}");
                sources.add(descriptor);
                options.addAll(List.of("--add-reads", "app.module=ALL-UNNAMED"));
            }
            final var task = compiler.getTask(new StringWriter(), manager, null, options, null,
                    standard.getJavaFileObjectsFromPaths(sources));
            task.setProcessors(List.of(new FusionProcessor()));
            final var failure = assertThrows(RuntimeException.class, task::call);
            assertTrue(failure.getCause().getMessage().contains("verifiable file class output"), failure.toString());
            assertFalse(Files.exists(work.resolve("state")));
        }
    }

    @Test
    void failedRootModulePublicationCannotSilentlyReuseUpdatedFragments() throws Exception {
        final var layout = layout("failed/classes", "failed/generated", "failed/state");
        compile(layout, Map.of("demo.A", bean("A", 1), "demo.B", bean("B", 2)), "modules.Main");
        final var source = layout.sources().resolve("demo/A.java");
        Files.writeString(source, bean("A", 3));
        final var compiler = ToolProvider.getSystemJavaCompiler();
        final var diagnostics = new StringWriter();
        try (final var standard = compiler.getStandardFileManager(null, null, null);
             final var manager = new ForwardingJavaFileManager<>(standard) {
                 @Override
                 public JavaFileObject getJavaFileForOutput(final JavaFileManager.Location location, final String name,
                                                            final JavaFileObject.Kind kind, final FileObject sibling) throws IOException {
                     if (kind == JavaFileObject.Kind.SOURCE && "modules.Main".equals(name)) {
                         throw new IOException("injected root module publication failure");
                     }
                     return super.getJavaFileForOutput(location, name, kind, sibling);
                 }
             }) {
            final var task = compiler.getTask(diagnostics, manager, null, List.of("--release", "17",
                    "-classpath", layout.output() + File.pathSeparator + System.getProperty("java.class.path"),
                    "-d", layout.output().toString(), "-s", layout.generated().toString(),
                    "-Afusion.workdir=" + layout.state(), "-Afusion.moduleFqn=modules.Main"), null,
                    standard.getJavaFileObjectsFromPaths(List.of(source)));
            task.setProcessors(List.of(new FusionProcessor()));
            assertFalse(task.call(), diagnostics.toString());
            assertTrue(diagnostics.toString().contains("injected root module publication failure"), diagnostics.toString());
        }
        final var failure = assertThrows(RuntimeException.class,
                () -> compile(layout, Map.of("demo.A", bean("A", 3)), "modules.Main"));
        assertTrue(failure.getCause().getMessage().contains("Incomplete compact output update"), failure.toString());
        // Initialization failure must release the explicit partition lock as well.
        assertTrue(assertThrows(RuntimeException.class,
                () -> compile(layout, Map.of("demo.A", bean("A", 3)), "modules.Main"))
                .getCause().getMessage().contains("full clean rebuild"));
    }

    private String bean(final String name, final int order) {
        return "package demo; @io.yupiik.fusion.framework.build.api.scanning.Bean " +
                "@io.yupiik.fusion.framework.build.api.order.Order(" + order + ") public class " + name +
                " { public " + name + "(io.yupiik.fusion.framework.api.configuration.Configuration configuration) {} }";
    }

    private void compile(final Layout layout, final Map<String, String> sources, final String module) throws Exception {
        Files.createDirectories(layout.output());
        Files.createDirectories(layout.generated());
        final var paths = new ArrayList<Path>();
        for (final var entry : new TreeMap<>(sources).entrySet()) {
            final var path = layout.sources().resolve(entry.getKey().replace('.', '/') + ".java");
            Files.createDirectories(path.getParent());
            Files.writeString(path, entry.getValue());
            paths.add(path);
        }
        final var compiler = ToolProvider.getSystemJavaCompiler();
        final var diagnostics = new StringWriter();
        try (final var manager = compiler.getStandardFileManager(null, null, null)) {
            final var task = compiler.getTask(diagnostics, manager, null, List.of("--release", "17",
                    "-Werror", "-Xlint:all,-processing,-serial", "-classpath",
                    layout.output() + File.pathSeparator + System.getProperty("java.class.path"),
                    "-d", layout.output().toString(), "-s", layout.generated().toString(),
                    "-Afusion.workdir=" + layout.state(), "-Afusion.moduleFqn=" + module), null,
                    manager.getJavaFileObjectsFromPaths(paths));
            task.setProcessors(List.of(new FusionProcessor()));
            assertTrue(task.call(), diagnostics.toString());
        }
    }

    private Map<String, String> tree(final Path root) throws Exception {
        final var result = new TreeMap<String, String>();
        try (final var files = Files.walk(root)) {
            for (final var path : files.filter(Files::isRegularFile).toList()) {
                result.put(root.relativize(path).toString(), Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
            }
        }
        return result;
    }

    private List<String> holders(final Layout layout) throws Exception {
        return Files.readAllLines(layout.output().resolveSibling(".fusion-" + layout.output().getFileName()).resolve("compact-holders-v1"));
    }

    private void assertRetired(final Layout layout, final List<String> names) {
        for (final var name : names) {
            assertFalse(Files.exists(layout.output().resolve(name.replace('.', '/') + ".class")), name);
            assertFalse(Files.exists(layout.generated().resolve(name.replace('.', '/') + ".java")), name);
        }
    }

    private List<String> beans(final Layout layout, final String module) throws Exception {
        try (final var loader = new URLClassLoader(new URL[]{layout.output().toUri().toURL()}, getClass().getClassLoader())) {
            return ((FusionModule) loader.loadClass(module).getConstructor().newInstance()).beans()
                    .map(it -> it.type().getTypeName()).sorted().toList();
        }
    }

    private Path jar(final Layout layout, final String name) throws Exception {
        final var jar = work.resolve(name);
        try (final var out = new JarOutputStream(Files.newOutputStream(jar));
             final var files = Files.walk(layout.output())) {
            for (final var file : files.filter(Files::isRegularFile).sorted().toList()) {
                out.putNextEntry(new JarEntry(layout.output().relativize(file).toString().replace('\\', '/')));
                Files.copy(file, out);
                out.closeEntry();
            }
        }
        return jar;
    }

    private record Layout(Path output, Path generated, Path state, Path sources) {
    }
}
