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

import io.yupiik.fusion.framework.processor.test.GeneratedFactories;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.serialization.JsonCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.File;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplicitJsonDependencyTest {
    @TempDir
    Path work;

    @Test
    void implicitModelsCrossThreeJarsWithoutSchemasOrRuntimeBuildApi() throws Exception {
        final var a = jar("a", Map.of(
                "implicit.a.Leaf", """
                        package implicit.a;
                        public record Leaf(@io.yupiik.fusion.framework.build.api.json.JsonProperty("text") String value,
                                           Mode mode, Extra extra) {
                            @io.yupiik.fusion.framework.build.api.json.JsonModel
                            public enum Mode {
                                READY;
                                public String toJsonString() { return "ready"; }
                            }
                            public record Extra(@io.yupiik.fusion.framework.build.api.json.JsonOthers
                                                java.util.Map<String, Object> attributes) {}
                        }
                        """), List.of());
        final var b = jar("b", Map.of("implicit.b.Middle", """
                package implicit.b;
                public record Middle(@io.yupiik.fusion.framework.build.api.json.JsonProperty implicit.a.Leaf leaf,
                                     java.util.List<implicit.a.Leaf.Extra> extras) {}
                """), List.of(a));
        final var c = jar("c", Map.of("implicit.c.Envelope", """
                package implicit.c;
                @io.yupiik.fusion.framework.build.api.json.JsonModel
                public record Envelope(implicit.b.Middle middle,
                                       java.util.Map<String, implicit.a.Leaf.Extra> indexed,
                                       implicit.a.Leaf.Mode mode) {}
                """), List.of(a, b));

        // The fixture loader cannot load build-api, even through its otherwise shared runtime parent.
        try (final var loader = new URLClassLoader(new URL[]{a.toUri().toURL(), b.toUri().toURL(), c.toUri().toURL()},
                getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("io.yupiik.fusion.framework.build.api.")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        }) {
            assertThrows(ClassNotFoundException.class,
                    () -> loader.loadClass("io.yupiik.fusion.framework.build.api.json.JsonProperty"));
            final var codecs = new ArrayList<JsonCodec<?>>();
            for (final var stage : List.of("a", "b", "c")) {
                final var pkg = "implicit." + stage;
                for (final var factory : GeneratedFactories.methods(loader, pkg, JsonCodec.class)) {
                    codecs.add(GeneratedFactories.invoke(loader, pkg, factory.getName(), JsonCodec.class));
                }
            }
            assertEquals(List.of("implicit.a.Leaf", "implicit.a.Leaf$Extra", "implicit.a.Leaf$Mode",
                            "implicit.b.Middle", "implicit.c.Envelope"),
                    codecs.stream().map(it -> it.type().getTypeName()).sorted().toList());
            try (final var mapper = new JsonMapperImpl(codecs, key -> Optional.empty())) {
                final var type = loader.loadClass("implicit.c.Envelope");
                final var json = "{\"indexed\":{\"key\":{\"dynamic\":\"value\"}},\"middle\":{\"extras\":[{\"flag\":true}]," +
                        "\"leaf\":{\"extra\":{\"unknown\":\"captured\"},\"mode\":\"ready\",\"text\":\"deep\"}},\"mode\":\"ready\"}";
                final var value = mapper.fromString(type, json);
                assertEquals(json, mapper.toString(value));
                assertEquals(value, mapper.fromString(type, mapper.toString(value)));
                final var middle = type.getMethod("middle").invoke(value);
                final var leaf = middle.getClass().getMethod("leaf").invoke(middle);
                assertEquals("deep", leaf.getClass().getMethod("value").invoke(leaf));
                final var extra = leaf.getClass().getMethod("extra").invoke(leaf);
                assertEquals(Map.of("unknown", "captured"), extra.getClass().getMethod("attributes").invoke(extra));
            }
        }
    }

    @Test
    void selectedUnannotatedSourceDoesNotInheritDependencyModelStatus() throws Exception {
        final var dependency = jar("original", Map.of("implicit.a.Leaf", """
                package implicit.a;
                public record Leaf(@io.yupiik.fusion.framework.build.api.json.JsonProperty String value) {}
                """), List.of());
        final var result = compile("shadow", Map.of(
                "implicit.a.Leaf", "package implicit.a; public record Leaf(String value) {}",
                "implicit.shadow.Envelope", """
                        package implicit.shadow;
                        @io.yupiik.fusion.framework.build.api.json.JsonModel
                        public record Envelope(implicit.a.Leaf leaf) {}
                        """), List.of(dependency), false);
        assertFalse(result.success(), result.diagnostics());
        assertTrue(result.diagnostics().contains("Leaf"), result.diagnostics());
    }

    @Test
    void nestedAnnotationDoesNotMarkItsEnclosingRecordAndIgnoreIsNotATrigger() throws Exception {
        final var dependency = jar("scope", Map.of("implicit.scope.Outer", """
                package implicit.scope;
                public record Outer(String value) {
                    public record Nested(@io.yupiik.fusion.framework.build.api.json.JsonProperty String value) {}
                    public record Ignored(@io.yupiik.fusion.framework.build.api.json.JsonIgnore String value) {}
                }
                """), List.of());
        for (final var type : List.of("Outer", "Outer.Ignored")) {
            final var result = compile("reject" + type.replace('.', '_'), Map.of("implicit.reject.Envelope", """
                    package implicit.reject;
                    @io.yupiik.fusion.framework.build.api.json.JsonModel
                    public record Envelope(implicit.scope.%s value) {}
                    """.formatted(type)), List.of(dependency), false);
            assertFalse(result.success(), result.diagnostics());
            assertTrue(result.diagnostics().contains(type.substring(type.lastIndexOf('.') + 1)), result.diagnostics());
        }
    }

    @Test
    void deletedImplicitSourceIsNotResurrectedFromStaleClassOutput() throws Exception {
        final var root = work.resolve("incremental");
        compile("incremental", Map.of("implicit.local.Leaf", """
                package implicit.local;
                public record Leaf(@io.yupiik.fusion.framework.build.api.json.JsonProperty String value) {}
                """), List.of(), true);
        Files.delete(root.resolve("sources/implicit/local/Leaf.java"));
        final var result = compile("incremental", Map.of("implicit.local.Envelope", """
                package implicit.local;
                @io.yupiik.fusion.framework.build.api.json.JsonModel
                public record Envelope(Leaf leaf) {}
                """), List.of(root.resolve("classes")), false);
        assertFalse(result.success(), result.diagnostics());
        assertTrue(result.diagnostics().contains("Leaf"), result.diagnostics());
    }

    @Test
    void removingImplicitAnnotationDoesNotReuseStaleLocalBindings() throws Exception {
        final var model = "implicit.removed.Leaf";
        compile("removed", Map.of(model, """
                package implicit.removed;
                public record Leaf(@io.yupiik.fusion.framework.build.api.json.JsonOthers
                                   java.util.Map<String, Object> values) {}
                """), List.of(), true);
        final var result = compile("removed", Map.of(
                model, "package implicit.removed; public record Leaf(java.util.Map<String, Object> values) {}",
                "implicit.removed.Envelope", """
                        package implicit.removed;
                        @io.yupiik.fusion.framework.build.api.json.JsonModel
                        public record Envelope(Leaf leaf) {}
                        """), List.of(work.resolve("removed/classes")), false);
        assertFalse(result.success(), result.diagnostics());
        assertTrue(result.diagnostics().contains("Leaf"), result.diagnostics());
    }

    private Path jar(final String stage, final Map<String, String> sources, final List<Path> dependencies) throws Exception {
        final var result = compile(stage, sources, dependencies, true);
        final var jar = work.resolve(stage + ".jar");
        try (final var out = new JarOutputStream(Files.newOutputStream(jar));
             final var files = Files.walk(result.output())) {
            for (final var file : files.filter(Files::isRegularFile).sorted().toList()) {
                out.putNextEntry(new JarEntry(result.output().relativize(file).toString().replace('\\', '/')));
                Files.copy(file, out);
                out.closeEntry();
            }
        }
        try (final var archive = new JarFile(jar.toFile())) {
            assertNull(archive.getEntry("META-INF/fusion/json/schemas.json"));
            for (final var entry : archive.stream().toList()) {
                final var name = entry.getName();
                assertFalse(name.contains("json-models") || name.contains("metadata-aliases") ||
                        name.startsWith("META-INF/fusion/json/") || name.endsWith("$FusionJsonCodec.class"), name);
            }
        }
        return jar;
    }

    private Compilation compile(final String stage, final Map<String, String> sources,
                                final List<Path> dependencies, final boolean expectedSuccess) throws Exception {
        final var root = Files.createDirectories(work.resolve(stage));
        final var output = Files.createDirectories(root.resolve("classes"));
        final var generated = Files.createDirectories(root.resolve("generated"));
        final var sourceRoot = Files.createDirectories(root.resolve("sources"));
        final var files = new ArrayList<File>();
        for (final var source : sources.entrySet()) {
            final var file = sourceRoot.resolve(source.getKey().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            files.add(file.toFile());
        }
        final var processorPath = System.getProperty("java.class.path");
        for (final var dependency : dependencies) {
            assertFalse(processorPath.contains(dependency.toString()), "Fixture dependencies must not be on processorpath");
        }
        final var classpath = dependencies.stream().map(Path::toString).collect(joining(File.pathSeparator));
        final var options = List.of("--release", "17", "-Xlint:all,-processing,-serial", "-Werror", "-classpath",
                (classpath.isEmpty() ? "" : classpath + File.pathSeparator) + processorPath,
                "-processorpath", processorPath, "-processor", FusionProcessor.class.getName(),
                "-d", output.toString(), "-s", generated.toString(),
                "-Afusion.workdir=" + root.resolve("state"), "-Afusion.moduleFqn=implicit.generated." + stage + ".Module",
                "-Afusion.generateBeanForJsonCodec=false", "-Afusion.generateNativeImage=false",
                "-Afusion.generateJsonSchemas=false", "-Afusion.generatePartialOpenRPC=false",
                "-Afusion.skipLoadingModules=true");
        final var compiler = ToolProvider.getSystemJavaCompiler();
        final var diagnostics = new StringWriter();
        final boolean success;
        try (final var manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            success = compiler.getTask(diagnostics, manager, null, options, null,
                    manager.getJavaFileObjectsFromFiles(files)).call();
        }
        if (expectedSuccess) {
            assertTrue(success, () -> stage + " compilation failed:\n" + diagnostics);
        }
        return new Compilation(output, success, diagnostics.toString());
    }

    private record Compilation(Path output, boolean success, String diagnostics) {}
}
