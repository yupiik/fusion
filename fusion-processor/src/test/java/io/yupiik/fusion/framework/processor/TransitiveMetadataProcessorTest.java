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
import io.yupiik.fusion.framework.build.api.metadata.spi.MetadataContributor;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransitiveMetadataProcessorTest {
    @TempDir
    Path work;

    @Test
    void classRetainedMetadataWorksAcrossThreeCompilationStagesWithoutArtifactIndexes() throws Exception {
        final var a = compileJar("a", Map.of(
                "transitive.a.Flag", """
                        package transitive.a;
                        @io.yupiik.fusion.framework.build.api.metadata.BeanMetadataAlias(name="kind", value="from-a")
                        @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
                        @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE)
                        public @interface Flag {}
                        """,
                "transitive.a.Leaf", """
                        package transitive.a;
                        @io.yupiik.fusion.framework.build.api.json.JsonModel
                        public record Leaf(String value, Mode mode) {
                            @io.yupiik.fusion.framework.build.api.json.JsonModel
                            public enum Mode {
                                READY;
                                public String toJsonString() { return "ready-from-a"; }
                            }
                        }
                        """), List.of());
        final var b = compileJar("b", Map.of(
                "transitive.b.Middle", """
                        package transitive.b;
                        @io.yupiik.fusion.framework.build.api.json.JsonModel
                        public record Middle(transitive.a.Leaf leaf) {}
                        """,
                "transitive.b.Service", """
                        package transitive.b;
                        @transitive.a.Flag
                        @io.yupiik.fusion.framework.build.api.scanning.Bean
                        public class Service {}
                        """), List.of(a));
        // A remains on C's effective classpath, as it would for a Maven transitive dependency.
        // Only B appears in C's model source; its leaf reaches A through B's compiled signature.
        final var c = compileJar("c", Map.of(
                "transitive.c.Envelope", """
                        package transitive.c;
                        @io.yupiik.fusion.framework.build.api.json.JsonModel
                        public record Envelope(transitive.b.Middle middle) {}
                        """,
                "transitive.c.Service", """
                        package transitive.c;
                        @transitive.a.Flag
                        @io.yupiik.fusion.framework.build.api.scanning.Bean
                        public class Service {}
                        """), List.of(a, b));

        try (final var loader = new URLClassLoader(new URL[]{c.toUri().toURL(), b.toUri().toURL(), a.toUri().toURL()},
                getClass().getClassLoader())) {
            for (final var stage : List.of("b", "c")) {
                final var bean = GeneratedFactories.bean(loader, loader.loadClass("transitive." + stage + ".Service"));
                assertEquals(Map.of("kind", "from-a"), bean.data(), stage + " must resolve A's class-retained alias");
            }

            final var codecs = new ArrayList<JsonCodec<?>>();
            for (final var stage : List.of("a", "b", "c")) {
                final var packageName = "transitive." + stage;
                for (final var method : GeneratedFactories.methods(loader, packageName, JsonCodec.class)) {
                    codecs.add(GeneratedFactories.invoke(loader, packageName, method.getName(), JsonCodec.class));
                }
            }
            assertEquals(List.of("transitive.a.Leaf", "transitive.a.Leaf$Mode", "transitive.b.Middle", "transitive.c.Envelope"),
                    codecs.stream().map(it -> it.type().getTypeName()).sorted().toList());
            try (final var mapper = new JsonMapperImpl(codecs, key -> Optional.empty())) {
                final var envelopeType = loader.loadClass("transitive.c.Envelope");
                final var json = "{\"middle\":{\"leaf\":{\"mode\":\"ready-from-a\",\"value\":\"deep\"}}}";
                final var envelope = mapper.fromString(envelopeType, json);
                final var middle = envelopeType.getMethod("middle").invoke(envelope);
                final var leaf = loader.loadClass("transitive.b.Middle").getMethod("leaf").invoke(middle);
                final var leafType = loader.loadClass("transitive.a.Leaf");
                assertEquals("deep", leafType.getMethod("value").invoke(leaf));
                assertEquals("READY", ((Enum<?>) leafType.getMethod("mode").invoke(leaf)).name());
                assertEquals(json, mapper.toString(envelope));
                assertEquals(envelope, mapper.fromString(envelopeType, mapper.toString(envelope)));
                final var constructedLeaf = leafType.getConstructor(String.class, loader.loadClass("transitive.a.Leaf$Mode"))
                        .newInstance("written", leafType.getMethod("mode").invoke(leaf));
                final var constructedMiddle = loader.loadClass("transitive.b.Middle").getConstructor(leafType).newInstance(constructedLeaf);
                final var constructedEnvelope = envelopeType.getConstructor(middle.getClass()).newInstance(constructedMiddle);
                assertEquals("{\"middle\":{\"leaf\":{\"mode\":\"ready-from-a\",\"value\":\"written\"}}}", mapper.toString(constructedEnvelope));
            }
        }
    }

    private Path compileJar(final String stage, final Map<String, String> sources, final List<Path> dependencies) throws Exception {
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
            assertFalse(processorPath.contains(dependency.toString()), "Dependencies belong only on javac's compilation classpath");
            final var tccl = Thread.currentThread().getContextClassLoader();
            if (tccl != null) {
                final var model = dependency.getFileName().toString().equals("a.jar") ? "transitive/a/Leaf.class" : "transitive/b/Middle.class";
                assertNull(tccl.getResource(model), "Dependency must not be visible to the processor through the TCCL");
            }
        }
        final var classpath = dependencies.stream().map(Path::toString).collect(joining(File.pathSeparator));
        final var options = List.of("--release", "17", "-Xlint:all,-processing,-serial", "-Werror", "-classpath",
                (classpath.isEmpty() ? "" : classpath + File.pathSeparator) + processorPath,
                "-processorpath", processorPath, "-processor", FusionProcessor.class.getName(),
                "-d", output.toString(), "-s", generated.toString(),
                "-Afusion.workdir=" + root.resolve("state"), "-Afusion.moduleFqn=transitive." + stage + ".Module",
                "-Afusion.generateBeanForJsonCodec=false", "-Afusion.generateNativeImage=false",
                "-Afusion.generateJsonSchemas=false", "-Afusion.generatePartialOpenRPC=false",
                "-Afusion.skipLoadingModules=true");
        final var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Test requires a JDK");
        final var diagnostics = new StringWriter();
        try (final var manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            final var task = compiler.getTask(diagnostics, manager, null, options, null, manager.getJavaFileObjectsFromFiles(files));
            assertTrue(task.call(), () -> stage + " compilation failed:\n" + diagnostics);
        }
        final var jar = work.resolve(stage + ".jar");
        try (final var out = new JarOutputStream(Files.newOutputStream(jar));
             final var entries = Files.walk(output)) {
            for (final var file : entries.filter(Files::isRegularFile).sorted().toList()) {
                out.putNextEntry(new JarEntry(output.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, out);
                out.closeEntry();
            }
        }
        try (final var archive = new JarFile(jar.toFile())) {
            assertNull(archive.getEntry("transitive/" + stage + "/FusionFactories.class"));
            assertTrue(archive.stream().anyMatch(it -> it.getName().startsWith("transitive/" + stage + "/") &&
                    (it.getName().contains("FusionBindings_") || it.getName().endsWith("/Module.class"))),
                    "Standalone constructions must be packaged on an actual construction owner");
            assertNull(archive.getEntry("META-INF/services/io.yupiik.fusion.framework.api.spi.dispatch.BindingProvider"));
            assertNotNull(archive.getEntry("META-INF/services/io.yupiik.fusion.framework.api.container.FusionModule"));
            for (final var entry : archive.stream().toList()) {
                final var name = entry.getName();
                assertFalse(name.startsWith("META-INF/fusion/json/"), name);
                assertFalse(name.startsWith("META-INF/fusion/metadata/"), name);
                assertFalse(name.contains("openrpc") || name.contains("metadata-aliases") || name.contains("FusionMetadataContributor"), name);
                assertFalse(name.equals("META-INF/services/" + MetadataContributor.class.getName()), name);
                if (name.endsWith(".class")) {
                    try (final var in = archive.getInputStream(entry)) {
                        final var constants = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
                        assertFalse(constants.contains(MetadataContributor.class.getName().replace('.', '/')), name);
                    }
                }
            }
        }
        return jar;
    }
}
