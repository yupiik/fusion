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
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.serialization.JsonCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.File;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonNativePackagingTest {
    @TempDir
    Path work;

    @Test
    void classRetainedModelsRunWithoutTheBuildApi() throws Exception {
        compile(Map.of("demo.Model", model("Model")), true);
        final var runtime = new URL[]{work.resolve("classes").toUri().toURL(),
                FusionBean.class.getProtectionDomain().getCodeSource().getLocation(),
                JsonMapper.class.getProtectionDomain().getCodeSource().getLocation()};
        try (final var loader = new URLClassLoader(runtime, ClassLoader.getPlatformClassLoader())) {
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass(
                    "io.yupiik.fusion.framework.build.api.json.JsonModel"));
            final var type = loader.loadClass("demo.Model");
            assertEquals(0, type.getDeclaredAnnotations().length);
            final var codecType = loader.loadClass(JsonCodec.class.getName());
                final var codec = GeneratedFactories.invoke(loader, "demo", "Model$FusionJsonCodec", codecType);
            assertEquals(type, codecType.getMethod("type").invoke(codec));
        }
    }

    @Test
    void incrementalModelStateStaysOutsideClassOutput() throws Exception {
        compile(Map.of("demo.Model", model("Model")), true);
        compile(Map.of("demo.Unrelated", "package demo; public class Unrelated {}"), true);
        assertEquals(Set.of("demo.Model"), modelNames());
        assertFalse(Files.exists(work.resolve("classes/META-INF/fusion/json/models-v1")));
        assertFalse(Files.exists(work.resolve("classes/META-INF/fusion/metadata/aliases-v1")));
        assertFalse(Files.exists(work.resolve("classes/json-models-v1.properties")));
    }

    @Test
    void deletingSourcesDoesNotResurrectStaleLocalClassAnnotations() throws Exception {
        compile(Map.of("demo.Model", model("Model")), true);
        Files.delete(work.resolve("sources/demo/Model.java"));
        compile(Map.of("demo.Unrelated", "package demo; public class Unrelated {}"), true);
        assertEquals(Set.of(), modelNames());
        compile(Map.of("demo.Consumer", consumer("Model")), false);
    }

    @Test
    void missingIndexFailsSafeInsteadOfTrustingStaleLocalClassOutput() throws Exception {
        compile(Map.of("demo.Model", model("Model")), true);
        Files.delete(work.resolve("state/json-models-v1.properties"));
        compile(Map.of("demo.Consumer", consumer("Model")), false);
    }

    @Test
    void recompilingAnEnclosingNameDoesNotRemoveUnrelatedLiteralDollarModels() throws Exception {
        compile(Map.of("demo.Model", model("Model"), "demo.Model$Literal", model("Model$Literal")), true);
        compile(Map.of("demo.Model", model("Model")), true);
        assertEquals(Set.of("demo.Model", "demo.Model$Literal"), modelNames());
        compile(Map.of("demo.Consumer", consumer("Model$Literal")), true);
    }

    @Test
    void removedModelAnnotationsCannotBeRecoveredFromCachedSchemasByHttpGeneration() throws Exception {
        compile(Map.of("demo.Model", model("Model")), true, "-Afusion.generateJsonSchemas=true");
        compile(Map.of("demo.Model", "package demo; public record Model(String value) {}", "demo.Api", """
                package demo;
                public class Api {
                    @io.yupiik.fusion.framework.build.api.http.HttpMatcher
                    public Model model(Model input) { return input; }
                }
                """), false, "-Afusion.generateJsonSchemas=true");
    }

    @Test
    void unknownRpcModelsRequestCompilerMetadataRatherThanSchemaGeneration() throws Exception {
        final var diagnostics = compile(Map.of("demo.Model", "package demo; public record Model(String value) {}",
                "demo.Api", """
                        package demo;
                        public class Api {
                            @io.yupiik.fusion.framework.build.api.jsonrpc.JsonRpc("model")
                            public Model model() { return null; }
                        }
                        """), false, "-Afusion.generatePartialOpenRPC=true");
        assertTrue(diagnostics.contains("invalid type: 'demo.Model'"), diagnostics);
        assertTrue(diagnostics.contains("@JsonModel"), diagnostics);
        assertFalse(diagnostics.contains("check you enabled its generation"), diagnostics);
    }

    private String model(final String name) {
        return "package demo; @io.yupiik.fusion.framework.build.api.json.JsonModel public record " + name + "(String value) {}";
    }

    private String consumer(final String type) {
        return "package demo; @io.yupiik.fusion.framework.build.api.json.JsonModel public record Consumer(" + type + " value) {}";
    }

    private Set<String> modelNames() throws Exception {
        final var properties = new Properties();
        try (final var reader = Files.newBufferedReader(work.resolve("state/json-models-v1.properties"))) {
            properties.load(reader);
        }
        return properties.stringPropertyNames();
    }

    private String compile(final Map<String, String> sources, final boolean success, final String... extra) throws Exception {
        final var paths = new ArrayList<Path>();
        for (final var entry : sources.entrySet()) {
            final var path = work.resolve("sources/" + entry.getKey().replace('.', '/') + ".java");
            Files.createDirectories(path.getParent());
            Files.writeString(path, entry.getValue());
            paths.add(path);
        }
        Files.createDirectories(work.resolve("classes"));
        Files.createDirectories(work.resolve("generated"));
        final var compiler = ToolProvider.getSystemJavaCompiler();
        final var output = new StringWriter();
        try (final var manager = compiler.getStandardFileManager(null, null, null)) {
            final var options = new ArrayList<>(List.of("--release", "17", "-classpath",
                    work.resolve("classes") + File.pathSeparator + System.getProperty("java.class.path"),
                    "-d", work.resolve("classes").toString(), "-s", work.resolve("generated").toString(),
                    "-Afusion.workdir=" + work.resolve("state"), "-Afusion.moduleFqn=demo.Module",
                    "-Afusion.generateModule=false", "-Afusion.generateJsonSchemas=false",
                    "-Afusion.generatePartialOpenRPC=false", "-Xlint:all,-processing,-serial", "-Werror"));
            options.addAll(List.of(extra));
            final var task = compiler.getTask(output, manager, null, options, null, manager.getJavaFileObjectsFromPaths(paths));
            task.setProcessors(List.of(new FusionProcessor()));
            assertEquals(success, task.call(), output.toString());
            if (!success) {
                assertFalse(output.toString().contains("NullPointerException"), output.toString());
                assertTrue(output.toString().contains("Model") || output.toString().contains("Consumer"), output.toString());
            }
        }
        return output.toString();
    }
}
