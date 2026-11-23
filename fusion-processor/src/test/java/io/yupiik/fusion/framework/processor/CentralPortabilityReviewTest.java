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

import static io.yupiik.fusion.framework.processor.test.ProcessorInternals.stateDirectory;

import io.yupiik.fusion.framework.processor.test.GeneratedFactories;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.serialization.JsonCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CentralPortabilityReviewTest {
    @TempDir
    Path work;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void discoversExplicitAndImplicitModelsAcrossSourceModules(final boolean generateModule) throws Exception {
        compile("cross", Map.of(
                "model.module/module-info.java", "module model.module { exports model; }",
                "app.module/module-info.java", "module app.module { requires model.module; }",
                "model.module/model/Explicit.java", """
                        package model;
                        @io.yupiik.fusion.framework.build.api.json.JsonModel
                        public record Explicit(String explicitValue) {}
                        """,
                "model.module/model/Implicit.java", """
                        package model;
                        public record Implicit(@io.yupiik.fusion.framework.build.api.json.JsonProperty("text") String value) {}
                        """,
                "app.module/app/Envelope.java", """
                        package app;
                        @io.yupiik.fusion.framework.build.api.json.JsonModel
                        public record Envelope(model.Explicit explicit, model.Implicit implicit) {}
                        """), moduleOptions("cross", generateModule), List.of(new FusionProcessor()));
        try (final var loader = moduleLoader("cross")) {
            final var codecs = new ArrayList<JsonCodec<?>>();
            for (final var pack : List.of("model", "app")) {
                for (final var factory : GeneratedFactories.methods(loader, pack, JsonCodec.class)) {
                    codecs.add((JsonCodec<?>) factory.invoke(null));
                }
            }
            assertEquals(List.of("app.Envelope", "model.Explicit", "model.Implicit"),
                    codecs.stream().map(it -> it.type().getTypeName()).sorted().toList());
            try (final var mapper = new JsonMapperImpl(codecs, key -> Optional.empty())) {
                final var json = "{\"explicit\":{\"explicitValue\":\"one\"},\"implicit\":{\"text\":\"two\"}}";
                assertEquals(json, mapper.toString(mapper.fromString(loader.loadClass("app.Envelope"), json)));
            }
        }
        // Discovery must not import the producer's entries into consumer build state or generated artifacts.
        final var index = new Properties();
        try (final var in = Files.newBufferedReader(stateDirectory(work.resolve("cross/state"), work.resolve("cross/classes/app.module"), "app.module").resolve("json-models-v1.properties"))) {
            index.load(in);
        }
        assertEquals(Set.of("app.Envelope"), index.stringPropertyNames());
        assertFalse(generatedSources("cross", "app.module").contains("new model.Explicit("));
        assertFalse(generatedSources("cross", "app.module").contains("new model.Implicit("));
        assertNoCoordinationOutput("cross");
    }

    @Test
    void removesSecondaryDeclarationsBySourceUriDespiteStaleClasses() throws Exception {
        compile("secondary", Map.of(
                "demo/Owner.java", """
                        package demo;
                        @io.yupiik.fusion.framework.build.api.scanning.Bean
                        public class Owner {}
                        @io.yupiik.fusion.framework.build.api.scanning.Bean
                        class Removed {
                            void listen(@io.yupiik.fusion.framework.build.api.event.OnEvent String event) {}
                        }
                        @io.yupiik.fusion.framework.build.api.json.JsonModel
                        record RemovedModel(String value) {}
                        """,
                "demo/Preserved.java", """
                        package demo;
                        @io.yupiik.fusion.framework.build.api.scanning.Bean
                        public class Preserved {
                            void listen(@io.yupiik.fusion.framework.build.api.event.OnEvent String event) {}
                            @io.yupiik.fusion.framework.build.api.json.JsonModel
                            public record Model(String value) {}
                        }
                        """), List.of("-Afusion.generateBeanForJsonCodec=true"), List.of(new FusionProcessor()));
        try (final var loader = loader("secondary/classes")) {
            assertTrue(GeneratedFactories.beans(loader).stream().anyMatch(it -> it.type().getTypeName().equals("demo.Removed")));
            assertEquals(2, GeneratedFactories.listeners(loader).size());
            assertEquals(List.of("Preserved$Model$FusionJsonCodec", "RemovedModel$FusionJsonCodec"),
                    GeneratedFactories.methods(loader, "demo", JsonCodec.class).stream().map(it -> it.getName()).toList());
        }
        final var removedClass = work.resolve("secondary/classes/demo/Removed.class");
        final var modelClass = work.resolve("secondary/classes/demo/RemovedModel.class");
        final var removedBytes = Files.readAllBytes(removedClass);
        final var modelBytes = Files.readAllBytes(modelClass);
        compile("secondary", Map.of("demo/Owner.java", """
                package demo;
                @io.yupiik.fusion.framework.build.api.scanning.Bean
                public class Owner {}
                """), List.of("-Afusion.generateBeanForJsonCodec=true"), List.of(new FusionProcessor()));
        assertTrue(java.util.Arrays.equals(removedBytes, Files.readAllBytes(removedClass)), "Stale business class must remain untouched");
        assertTrue(java.util.Arrays.equals(modelBytes, Files.readAllBytes(modelClass)), "Stale model class must remain untouched");
        try (final var loader = loader("secondary/classes")) {
            final var beans = GeneratedFactories.beans(loader).stream().map(it -> it.type().getTypeName()).sorted().toList();
            assertTrue(beans.containsAll(List.of("demo.Owner", "demo.Preserved")), beans.toString());
            assertFalse(beans.stream().anyMatch(it -> it.contains("Removed")), beans.toString());
            assertEquals(1, GeneratedFactories.listeners(loader).size());
            assertEquals(List.of("Preserved$Model$FusionJsonCodec"),
                    GeneratedFactories.methods(loader, "demo", JsonCodec.class).stream().map(it -> it.getName()).toList());
        }
        assertNoCoordinationOutput("secondary");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void waitsForPeerChainInOtherModuleBeforeLateLocalModels(final boolean generateModule) throws Exception {
        compile("chain", Map.of(
                "model.module/module-info.java", "module model.module { exports model; }",
                "app.module/module-info.java", "module app.module { requires model.module; }",
                "model.module/model/Seed.java", "package model; public class Seed {}",
                "app.module/app/Early.java", """
                        package app;
                        @io.yupiik.fusion.framework.build.api.json.JsonModel
                        public record Early(String value) {}
                        """), moduleOptions("chain", generateModule), List.of(new FusionProcessor(), new CrossModulePeer()));
        try (final var loader = moduleLoader("chain")) {
            assertEquals(List.of("Early$FusionJsonCodec", "Late$FusionJsonCodec"),
                    GeneratedFactories.methods(loader, "app", JsonCodec.class).stream().map(it -> it.getName()).toList());
            final var codec = GeneratedFactories.invoke(loader, "app", "Late$FusionJsonCodec", JsonCodec.class);
            assertEquals("app.Late", codec.type().getTypeName());
            if (generateModule) {
                assertTrue(GeneratedFactories.beans(loader).stream().anyMatch(it -> it.type().getTypeName().equals("app.LateBean")));
            }
        }
        assertNoCoordinationOutput("chain");
    }

    private List<String> moduleOptions(final String name, final boolean generateModule) {
        return List.of("--module-source-path", work.resolve(name + "/src").toString(),
                "--add-reads", "model.module=ALL-UNNAMED", "--add-reads", "app.module=ALL-UNNAMED",
                "-Afusion.generateModule=" + generateModule, "-Afusion.generateBeanForJsonCodec=" + generateModule);
    }

    private URLClassLoader moduleLoader(final String name) throws IOException {
        return loader(name + "/classes/model.module", name + "/classes/app.module");
    }

    private URLClassLoader loader(final String... locations) throws IOException {
        final var urls = new ArrayList<URL>();
        for (final var location : locations) {
            urls.add(work.resolve(location).toUri().toURL());
        }
        return new URLClassLoader(urls.toArray(URL[]::new), getClass().getClassLoader());
    }

    private String generatedSources(final String name, final String module) throws IOException {
        final var result = new StringBuilder();
        try (final var files = Files.walk(work.resolve(name + "/generated/" + module))) {
            for (final var file : files.filter(it -> it.toString().endsWith(".java")).toList()) {
                result.append(Files.readString(file));
            }
        }
        return result.toString();
    }

    private void assertNoCoordinationOutput(final String name) throws IOException {
        for (final var location : List.of("classes", "generated")) {
            try (final var files = Files.walk(work.resolve(name + '/' + location))) {
                assertFalse(files.anyMatch(it -> Files.isRegularFile(it) && it.toString().contains("compact_")));
            }
        }
    }

    private void compile(final String name, final Map<String, String> sources,
                         final List<String> extra, final List<Processor> processors) throws IOException {
        final var root = work.resolve(name);
        Files.createDirectories(root.resolve("classes"));
        Files.createDirectories(root.resolve("generated"));
        final var paths = new ArrayList<Path>();
        for (final var source : sources.entrySet()) {
            final var path = root.resolve("src/" + source.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, source.getValue());
            paths.add(path);
        }
        final var options = new ArrayList<>(List.of("--release", "17", "-Werror", "-classpath",
                root.resolve("classes") + System.getProperty("path.separator") + System.getProperty("java.class.path"),
                "-d", root.resolve("classes").toString(), "-s", root.resolve("generated").toString(),
                "-Afusion.workdir=" + root.resolve("state")));
        options.addAll(extra);
        final var diagnostics = new StringWriter();
        final var compiler = ToolProvider.getSystemJavaCompiler();
        try (final var manager = compiler.getStandardFileManager(null, null, null)) {
            final var task = compiler.getTask(diagnostics, manager, null, options, null, manager.getJavaFileObjectsFromPaths(paths));
            task.setProcessors(processors);
            assertTrue(task.call(), diagnostics.toString());
        }
    }

    private static final class CrossModulePeer extends AbstractProcessor {
        private int round;

        @Override
        public Set<String> getSupportedAnnotationTypes() { return Set.of("*"); }
        @Override
        public SourceVersion getSupportedSourceVersion() { return SourceVersion.latestSupported(); }
        @Override
        public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment environment) {
            if (environment.processingOver()) {
                return false;
            }
            round++;
            try {
                if (round <= 5) {
                    final var pack = "model.chain.step" + round;
                    try (final var out = processingEnv.getFiler().createSourceFile("model.module/" + pack + ".package-info").openWriter()) {
                        out.write("package " + pack + ";");
                    }
                } else if (round == 6) {
                    try (final var out = processingEnv.getFiler().createSourceFile("app.module/app.Late").openWriter()) {
                        out.write("package app; @io.yupiik.fusion.framework.build.api.json.JsonModel public record Late(String value) {}");
                    }
                    try (final var out = processingEnv.getFiler().createSourceFile("app.module/app.LateBean").openWriter()) {
                        out.write("package app; @io.yupiik.fusion.framework.build.api.scanning.Bean public class LateBean {}");
                    }
                }
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            return false;
        }
    }
}
