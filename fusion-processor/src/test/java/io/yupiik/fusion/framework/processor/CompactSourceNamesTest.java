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

import io.yupiik.fusion.cli.internal.CliCommand;
import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.processor.test.GeneratedFactories;
import io.yupiik.fusion.http.server.impl.DefaultEndpoint;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.serialization.JsonCodec;
import io.yupiik.fusion.jsonrpc.impl.DefaultJsonRpcMethod;
import io.yupiik.fusion.jsonrpc.impl.JsonRpcMethod;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.tools.ToolProvider;
import java.io.File;
import java.io.PrintWriter;
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
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactSourceNamesTest {
    @TempDir
    Path work;

    @ParameterizedTest
    @ValueSource(strings = {"demo", ""})
    void sourceNamesAreNotBinaryNames(final String pkg) throws Exception {
        final var prefix = pkg.isEmpty() ? "" : pkg + ".";
        final var sources = Map.of(
                "A$id", """
                        import io.yupiik.fusion.framework.api.scope.*;
                        import io.yupiik.fusion.framework.build.api.scanning.*;
                        import io.yupiik.fusion.framework.build.api.event.*;
                        import io.yupiik.fusion.framework.build.api.http.*;
                        import io.yupiik.fusion.framework.build.api.jsonrpc.*;
                        import io.yupiik.fusion.framework.build.api.json.*;
                        import io.yupiik.fusion.http.server.api.*;
                        @ApplicationScoped public class A$id {
                            public static String trace = "";
                            public String value() { return "lazy"; }
                            public void listen(@OnEvent Event$id event) { trace += event.value(); }
                            @HttpMatcher(path="/ok") public Response http() { return Response.of().status(203).build(); }
                            @HttpJavaMatcher("req != null") public Response java() { return Response.of().status(204).build(); }
                            @JsonRpc("rpc") public String rpc(Model$id value) { return value.name(); }
                            @DefaultScoped public static class Factory$id<T> {
                                @Bean public String produce() { return "produced"; }
                                @HttpMatcher(path="/generic") public Response http() { return Response.of().status(205).build(); }
                                @JsonRpc("generic") public String rpc() { return "generic"; }
                            }
                            @ApplicationScoped public static class Lazy$id {
                                public String value() { return "nested-lazy"; }
                            }
                            public record Event$id(String value) {}
                            @JsonModel public record Nested$id(String value) {}
                        }
                        """,
                "Model$id", """
                        @io.yupiik.fusion.framework.build.api.json.JsonModel
                        public record Model$id(String name, E$id kind, A$id.Nested$id nested) {}
                        """,
                "E$id", """
                        @io.yupiik.fusion.framework.build.api.json.JsonModel
                        public enum E$id { YES, NO }
                        """,
                "Config$id", """
                        import io.yupiik.fusion.framework.build.api.configuration.*;
                        @RootConfiguration("app") public record Config$id(@Property String name) {}
                        """,
                "Run$id", """
                        @io.yupiik.fusion.framework.build.api.cli.Command(name="run", description="run")
                        public class Run$id implements Runnable {
                            private final Config$id config;
                            public Run$id(Config$id config, A$id bean) { this.config = config; }
                            public void run() { A$id.trace += config.name(); }
                        }
                        """);
        final var sourceFiles = new ArrayList<File>();
        for (final var entry : sources.entrySet()) {
            final var file = work.resolve("sources").resolve(pkg.replace('.', '/')).resolve(entry.getKey() + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, (pkg.isEmpty() ? "" : "package " + pkg + ";\n") + entry.getValue());
            sourceFiles.add(file.toFile());
        }
        final var classes = Files.createDirectories(work.resolve("classes"));
        final var generated = Files.createDirectories(work.resolve("generated"));
        final var compiler = ToolProvider.getSystemJavaCompiler();
        final var output = new StringWriter();
        try (final var manager = compiler.getStandardFileManager(null, null, null)) {
            final var task = compiler.getTask(output, manager, null, List.of(
                    "--release", "17", "-classpath", System.getProperty("java.class.path"),
                    "-d", classes.toString(), "-s", generated.toString(), "-Werror", "-Xlint:all,-processing,-serial",
                    "-Afusion.workdir=" + work.resolve("state"), "-Afusion.moduleFqn=" + prefix + "Module"),
                    null, manager.getJavaFileObjectsFromFiles(sourceFiles));
            task.setProcessors(List.of(new FusionProcessor()));
            assertTrue(task.call(), output.toString());
        }
        final var dependencySource = work.resolve("dependency/Link$id.java");
        Files.createDirectories(dependencySource.getParent());
        Files.writeString(dependencySource, (pkg.isEmpty() ? "" : "package " + pkg + ";\n") +
                "@io.yupiik.fusion.framework.build.api.json.JsonModel public record Link$id(Model$id model, A$id.Nested$id nested) {}\n");
        final var dependentClasses = Files.createDirectories(work.resolve("dependent-classes"));
        try (final var manager = compiler.getStandardFileManager(null, null, null)) {
            final var task = compiler.getTask(output, manager, null, List.of(
                    "--release", "17", "-classpath", classes + File.pathSeparator + System.getProperty("java.class.path"),
                    "-d", dependentClasses.toString(), "-s", Files.createDirectories(work.resolve("dependent-generated")).toString(),
                    "-Werror", "-Xlint:all,-processing,-serial", "-Afusion.workdir=" + work.resolve("dependent-state"),
                    "-Afusion.generateBeanForJsonCodec=false", "-Afusion.generateModule=false",
                    "-Afusion.generateJsonSchemas=false", "-Afusion.generatePartialOpenRPC=false"),
                    null, manager.getJavaFileObjects(dependencySource.toFile()));
            task.setProcessors(List.of(new FusionProcessor()));
            assertTrue(task.call(), output.toString());
        }
        try (final var loader = new URLClassLoader(new URL[]{classes.toUri().toURL(), dependentClasses.toUri().toURL()}, getClass().getClassLoader())) {
            final var module = (FusionModule) loader.loadClass(prefix + "Module").getConstructor().newInstance();
            final var modelType = loader.loadClass(prefix + "Model$id");
            final var codecs = new ArrayList<JsonCodec<?>>();
            for (final var name : List.of("Model$id", "E$id", "A$id$Nested$id", "Link$id")) {
                codecs.add(GeneratedFactories.invoke(loader, prefix.isEmpty() ? "" : prefix.substring(0, prefix.length() - 1), name + "$FusionJsonCodec", JsonCodec.class));
            }
            try (final var mapper = new JsonMapperImpl(codecs, key -> Optional.empty());
                 final var container = ConfiguringContainer.of().disableAutoDiscovery(true).register(module).start()) {
                final var beanType = loader.loadClass(prefix + "A$id");
                try (final var bean = container.lookup(beanType); final var produced = container.lookup(String.class)) {
                    assertTrue(bean.instance().getClass().getName().endsWith("$FusionSubclass"));
                    assertEquals("lazy", beanType.getMethod("value").invoke(bean.instance()));
                    assertEquals("produced", produced.instance());
                    final var nestedType = loader.loadClass(prefix + "A$id$Lazy$id");
                    try (final var nested = container.lookup(nestedType)) {
                        assertTrue(nested.instance().getClass().getName().endsWith("$FusionSubclass"));
                        assertEquals("nested-lazy", nestedType.getMethod("value").invoke(nested.instance()));
                    }
                    try (final var generic = container.lookup(loader.loadClass(prefix + "A$id$Factory$id"))) {
                        final var genericEndpoint = CompactSourceNamesTest.<Object[], DefaultEndpoint>factory(loader, prefix + "A$id$Factory$id$http$FusionHttpEndpoint")
                                .apply(new Object[]{generic.instance(), mapper});
                        assertEquals(205, genericEndpoint.handle(null).toCompletableFuture().join().status());
                        final var genericRpc = CompactSourceNamesTest.<Object[], DefaultJsonRpcMethod>factory(loader, prefix + "A$id$Factory$id$rpc$FusionJsonRpcMethod")
                                .apply(new Object[]{generic.instance(), mapper});
                        assertEquals("generic", genericRpc.invoke(new JsonRpcMethod.Context(null, Map.of())).toCompletableFuture().join());
                    }
                    container.emit(loader.loadClass(prefix + "A$id$Event$id").getConstructor(String.class).newInstance("event;"));
                    final var json = "{\"name\":\"json\",\"kind\":\"YES\",\"nested\":{\"value\":\"nested\"}}";
                    final Object model = mapper.fromString(modelType, json);
                    assertEquals("json", modelType.getMethod("name").invoke(model));
                    assertEquals("YES", modelType.getMethod("kind").invoke(model).toString());
                    assertEquals("nested", loader.loadClass(prefix + "A$id$Nested$id").getMethod("value").invoke(modelType.getMethod("nested").invoke(model)));
                    assertEquals(model, mapper.fromString(modelType, mapper.toString(model)));
                    final var linkType = loader.loadClass(prefix + "Link$id");
                    final Object link = mapper.fromString(linkType, "{\"model\":" + json + ",\"nested\":{\"value\":\"dependency\"}}");
                    assertEquals(model, linkType.getMethod("model").invoke(link));
                    assertEquals(link, mapper.fromString(linkType, mapper.toString(link)));
                    final var rpc = CompactSourceNamesTest.<Object[], DefaultJsonRpcMethod>factory(loader, prefix + "A$id$rpc$FusionJsonRpcMethod")
                            .apply(new Object[]{bean.instance(), mapper});
                    assertEquals("json", rpc.invoke(new JsonRpcMethod.Context(null, Map.of("value", Map.of("name", "json", "kind", "YES", "nested", Map.of("value", "nested"))))).toCompletableFuture().join());
                    final var endpoint = CompactSourceNamesTest.<Object[], DefaultEndpoint>factory(loader, prefix + "A$id$http$FusionHttpEndpoint")
                            .apply(new Object[]{bean.instance(), mapper});
                    assertEquals(203, endpoint.handle(null).toCompletableFuture().join().status());
                    final var javaEndpoint = CompactSourceNamesTest.<Object[], DefaultEndpoint>factory(loader, prefix + "A$id$java$FusionHttpJavaEndpoint")
                            .apply(new Object[]{bean.instance(), mapper});
                    assertEquals(204, javaEndpoint.handle(null).toCompletableFuture().join().status());
                    final var command = CompactSourceNamesTest.<RuntimeContainer, CliCommand<?>>factory(loader, prefix + "Run$id$FusionCliCommand").apply(container);
                    final Configuration configuration = key -> Optional.ofNullable(Map.of("app.name", "cli").get(key));
                    final var dependents = new ArrayList<Instance<?>>();
                    try (final var runnable = command.create(configuration, dependents)) {
                        ((Runnable) runnable.instance()).run();
                    } finally {
                        for (final var dependent : dependents) {
                            dependent.close();
                        }
                    }
                    assertEquals("event;cli", beanType.getField("trace").get(null));
                }
            }
        }
        try (final var files = Files.walk(classes)) {
            for (final var file : files.filter(p -> p.toString().endsWith(".class") &&
                    (p.getFileName().toString().startsWith("Fusion") || p.getFileName().toString().startsWith("Module"))).toList()) {
                final var constants = Files.readString(file, StandardCharsets.ISO_8859_1);
                final var bytecode = new StringWriter();
                final var type = classes.relativize(file).toString().replace(File.separatorChar, '.').replace(".class", "");
                assertEquals(0, java.util.spi.ToolProvider.findFirst("javap").orElseThrow().run(new PrintWriter(bytecode), new PrintWriter(bytecode),
                        "-c", "-p", "-classpath", classes + File.pathSeparator + System.getProperty("java.class.path"), type), bytecode.toString());
                assertFalse(bytecode.toString().lines().anyMatch(line -> line.contains("invokeinterface") && line.contains("Dispatch.invoke:")), bytecode.toString());
                assertFalse(constants.contains("java/lang/reflect/Method"), file.toString());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <D, T> Function<D, T> factory(final ClassLoader loader, final String id) {
        return (Function<D, T>) GeneratedFactories.invoke(loader, (id.contains(".") ? id.substring(0, id.lastIndexOf('.')) : ""), id.substring(id.lastIndexOf('.') + 1), Function.class);
    }
}
