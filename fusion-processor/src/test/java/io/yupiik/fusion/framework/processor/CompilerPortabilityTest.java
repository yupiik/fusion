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

import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.yupiik.fusion.framework.processor.test.ProcessorInternals.stateDirectory;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompilerPortabilityTest {
    @TempDir
    Path work;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ecjCompilesModelsAndDependencyReferencesWithoutJavacTrees(final boolean implicit) throws Exception {
        final var compiler = new EclipseCompiler();
        final var dependency = compile(compiler, "dependency", Map.of("dep/Leaf.java", """
                package dep;
                %s
                public record Leaf(@io.yupiik.fusion.framework.build.api.json.JsonProperty("renamed") String value) {
                    public record Extra(@io.yupiik.fusion.framework.build.api.json.JsonOthers java.util.Map<String, Object> values) {}
                }
                """.formatted(implicit ? "" : "@io.yupiik.fusion.framework.build.api.json.JsonModel")),
                List.of(), List.of(new FusionProcessor()));
        assertTrue(dependency.success(), dependency.diagnostics());
        assertNoCoordinationSources("dependency");
        final var result = compile(new EclipseCompiler(), "consumer", Map.of("app/Envelope.java", """
                package app;
                @io.yupiik.fusion.framework.build.api.json.JsonModel
                public record Envelope(dep.Leaf leaf, dep.Leaf.Extra extra) {}
                """), List.of("-classpath", work.resolve("dependency/classes") + System.getProperty("path.separator") + classpath()),
                List.of(new FusionProcessor()));
        assertTrue(result.success(), result.diagnostics());
        assertTrue(hasHolder(work.resolve("consumer/classes/app")));
        assertNoCoordinationSources("consumer");
    }

    @Test
    void ecjSourceReplacementCannotInheritDependencyModelStatus() throws Exception {
        final var compiler = new EclipseCompiler();
        final var dependency = compile(compiler, "dependency", Map.of("dep/Leaf.java", """
                package dep;
                @io.yupiik.fusion.framework.build.api.json.JsonModel
                public record Leaf(String value) {}
                """), List.of(), List.of(new FusionProcessor()));
        assertTrue(dependency.success(), dependency.diagnostics());
        final var result = compile(compiler, "replacement", Map.of(
                "dep/Leaf.java", "package dep; public record Leaf(String value) {}",
                "app/Envelope.java", """
                package app;
                @io.yupiik.fusion.framework.build.api.json.JsonModel
                public record Envelope(dep.Leaf leaf) {}
                """), List.of("-classpath", work.resolve("dependency/classes") + System.getProperty("path.separator") + classpath()),
                List.of(new FusionProcessor()));
        assertFalse(result.success(), "An unannotated source replacement must not reuse a dependency codec");
        assertFalse(result.diagnostics().contains("Trees"), result.diagnostics());
    }

    @Test
    void ecjRecompilesAnExistingGeneratedModuleWithoutAnOutputProbeResource() throws Exception {
        final var sources = Map.of("demo/Service.java", """
                package demo;
                @io.yupiik.fusion.framework.build.api.scanning.Bean
                public class Service {}
                """);
        final var first = compile(new EclipseCompiler(), "module-state", sources, List.of(), List.of(new FusionProcessor()));
        assertTrue(first.success(), first.diagnostics());
        final var second = compile(new EclipseCompiler(), "module-state", sources, List.of(), List.of(new FusionProcessor()));
        assertTrue(second.success(), second.diagnostics());
        try (final var files = Files.walk(work.resolve("module-state/classes"))) {
            assertFalse(files.anyMatch(it -> it.getFileName().toString().equals("fusion_marker_not_used_anywhere")));
        }
    }

    @Test
    void ecjHandlesSourcePathRediscoveryAndRetiresRemovedAnnotationsOnFullRecompilation() throws Exception {
        final var sources = Map.of(
                "alpha/First.java", "package alpha; @io.yupiik.fusion.framework.build.api.json.JsonModel public record First(String value) {}",
                "beta/Second.java", "package beta; @io.yupiik.fusion.framework.build.api.json.JsonModel public record Second(String value) {}");
        final var options = List.of("-Afusion.generateModule=false");
        final var first = compile(new EclipseCompiler(), "incremental", sources, options, List.of(new FusionProcessor()));
        assertTrue(first.success(), first.diagnostics());
        assertTrue(Files.exists(stateDirectory(work.resolve("incremental/state"), work.resolve("incremental/classes"), "").resolve("compact-v1.bin")), "Explicit workdir must work without a javac output URI");
        final var partial = compile(new EclipseCompiler(), "incremental", Map.of("alpha/First.java", sources.get("alpha/First.java")),
                options, List.of(new FusionProcessor()));
        assertTrue(partial.success(), partial.diagnostics());
        assertTrue(hasHolder(work.resolve("incremental/classes/beta")), "An untouched source origin must retain its codec");
        final var complete = compile(new EclipseCompiler(), "incremental", Map.of(
                "alpha/First.java", sources.get("alpha/First.java"),
                "beta/Second.java", "package beta; public record Second(String value) {}"), options, List.of(new FusionProcessor()));
        assertTrue(complete.success(), complete.diagnostics());
        assertTrue(Files.exists(work.resolve("incremental/classes/beta/Second.class")));
        assertFalse(hasHolder(work.resolve("incremental/classes/beta")));
        assertNoCoordinationSources("incremental");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void javacPartitionsNamedModulesEvenWithIdenticalModelNames(final boolean generateModule) throws Exception {
        final var result = compile(ToolProvider.getSystemJavaCompiler(), "modules", Map.of(
                "first.module/module-info.java", "module first.module {}",
                "second.module/module-info.java", "module second.module {}",
                "first.module/example/Model.java", """
                package example;
                @io.yupiik.fusion.framework.build.api.json.JsonModel
                public record Model(String first) {}
                """,
                "second.module/example/Model.java", """
                package example;
                @io.yupiik.fusion.framework.build.api.json.JsonModel
                public record Model(int second) {}
                """), List.of("--module-source-path", work.resolve("modules/src").toString(),
                "--add-reads", "first.module=ALL-UNNAMED", "--add-reads", "second.module=ALL-UNNAMED",
                "-Afusion.generateModule=" + generateModule, "-Afusion.generateBeanForJsonCodec=" + generateModule), List.of(new FusionProcessor()));
        assertTrue(result.success(), result.diagnostics());
        for (final var module : List.of("first.module", "second.module")) {
            final var output = work.resolve("modules/classes/" + module + "/example");
            assertTrue(Files.exists(output.resolve("Model.class")));
            assertTrue(generateModule ? Files.exists(output.resolve("FusionGeneratedModule.class")) || hasHolder(output) : hasHolder(output));
        }
        assertNoCoordinationSources("modules");
        for (final var module : List.of("first.module", "second.module")) {
            try (final var files = Files.walk(work.resolve("modules/generated/" + module))) {
                final var source = new StringBuilder();
                for (final var file : files.filter(it -> it.toString().endsWith(".java")).toList()) {
                    source.append(Files.readString(file));
                }
                assertTrue(source.toString().contains(module.equals("first.module") ? "::first" : "::second"), source.toString());
                assertFalse(source.toString().contains(module.equals("first.module") ? "::second" : "::first"), source.toString());
            }
        }
        try (final var files = Files.walk(work.resolve("modules/generated"))) {
            final var generated = files.filter(it -> it.toString().endsWith(".java")).toList();
            assertTrue(generated.stream().allMatch(it -> it.startsWith(work.resolve("modules/generated/first.module")) ||
                    it.startsWith(work.resolve("modules/generated/second.module"))), generated.toString());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void waitsForPeerSourcesProducedDuringQuietRounds(final boolean packageChain) throws Exception {
        final var result = compile(ToolProvider.getSystemJavaCompiler(), "rounds",
                Map.of("seed/Seed.java", "package seed; public class Seed {}"), List.of(),
                List.of(new FusionProcessor(), new DelayedModels(packageChain)));
        assertTrue(result.success(), result.diagnostics());
        assertTrue(hasHolder(work.resolve("rounds/classes/delayed")));
        assertNoCoordinationSources("rounds");
    }

    private boolean hasHolder(final Path directory) throws IOException {
        try (final var files = Files.list(directory)) {
            return files.anyMatch(it -> it.getFileName().toString().startsWith("FusionBindings_") && it.toString().endsWith(".class"));
        }
    }

    private void assertNoCoordinationSources(final String compilation) throws IOException {
        for (final var location : List.of("generated", "classes")) {
            try (final var files = Files.walk(work.resolve(compilation + "/" + location))) {
                assertFalse(files.anyMatch(it -> it.toString().contains("compact_") && Files.isRegularFile(it)),
                        "Coordination output must not be retained in " + location);
            }
        }
    }

    private Result compile(final JavaCompiler compiler, final String name, final Map<String, String> sources,
                           final List<String> extra, final List<Processor> processors) throws IOException {
        final var root = work.resolve(name);
        Files.createDirectories(root.resolve("classes"));
        Files.createDirectories(root.resolve("generated"));
        final var paths = new ArrayList<Path>();
        for (final var source : sources.entrySet()) {
            final var file = root.resolve("src/" + source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            paths.add(file);
        }
        final var options = new ArrayList<>(List.of("--release", "17",
                compiler instanceof EclipseCompiler ? "-failOnWarning" : "-Werror", "-classpath", classpath(),
                "-d", root.resolve("classes").toString(), "-s", root.resolve("generated").toString(),
                "-Afusion.workdir=" + root.resolve("state"), "-Afusion.generateBeanForJsonCodec=false"));
        if (compiler instanceof EclipseCompiler) {
            // Match javac's warning policy for shared generated suppressions; all remaining warnings are fatal.
            options.add("-warn:-warningToken");
            options.addAll(List.of("-sourcepath", root.resolve("src").toString()));
        }
        options.addAll(extra);
        final var diagnostics = new StringWriter();
        try (final var manager = compiler.getStandardFileManager(null, null, null)) {
            final var task = compiler.getTask(diagnostics, manager, null, options, null, manager.getJavaFileObjectsFromPaths(paths));
            task.setProcessors(processors);
            return new Result(task.call(), diagnostics.toString());
        }
    }

    private String classpath() {
        return System.getProperty("java.class.path");
    }

    private record Result(boolean success, String diagnostics) {}

    private static final class DelayedModels extends AbstractProcessor {
        private final boolean packageChain;
        private int round;

        private DelayedModels(final boolean packageChain) {
            this.packageChain = packageChain;
        }

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
                if (packageChain && round <= 5) {
                    final var pack = "chain.step" + round;
                    try (final var out = processingEnv.getFiler().createSourceFile(pack + ".package-info").openWriter()) {
                        out.write("package " + pack + ";");
                    }
                } else if (round == (packageChain ? 6 : 2)) {
                    try (final var out = processingEnv.getFiler().createSourceFile("delayed.Model").openWriter()) {
                        out.write("package delayed; @io.yupiik.fusion.framework.build.api.json.JsonModel public record Model(String value) {}");
                    }
                }
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            return false;
        }
    }
}
