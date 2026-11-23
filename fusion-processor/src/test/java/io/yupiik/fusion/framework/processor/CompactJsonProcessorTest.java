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

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.framework.processor.test.GeneratedFactories;
import io.yupiik.fusion.framework.api.container.bean.FunctionalBean;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.serialization.JsonCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.tools.ToolProvider;
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
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactJsonProcessorTest {
    private static final Pattern LAMBDA_BOOTSTRAP = Pattern.compile(
            "(?m)^\\s*(\\d+): #[^\\r\\n]*java/lang/invoke/LambdaMetafactory\\.(?:altMetafactory|metafactory):");
    private static final Pattern DYNAMIC_INSTRUCTION = Pattern.compile(
            "(?m)^\\s*\\d+: invokedynamic[^\\r\\n]*// InvokeDynamic #(\\d+):");

    @TempDir
    Path work;

    @Test
    void directCallsPreserveOrdersDefaultsOthersIgnoredAndCustomEnums() throws Exception {
        compile(Map.of("demo.Models", """
                package demo;
                import io.yupiik.fusion.framework.build.api.json.*;
                import java.util.*;
                public class Models {
                    @JsonModel public enum Mode {
                        YES, NO;
                        public String toJsonString() { return name().toLowerCase(java.util.Locale.ROOT); }
                    }
                    @JsonModel public record Leaf(String value) {}
                }
                """, "demo.Envelope", """
                package demo;
                import io.yupiik.fusion.framework.build.api.json.*;
                import java.util.*;
                @JsonModel record Envelope(
                    @JsonProperty(order=2) List<Models.Leaf> list,
                    @JsonProperty(value="n\\\"m", order=1) String name,
                    @JsonProperty(order=0) Models.Mode mode,
                    int count, Integer boxed,
                    @JsonOthers Map<String, Object> extensions,
                    @JsonIgnore int ignored,
                    @JsonIgnore List<String> ignoredList) {}
                """));
        try (final var loader = loader()) {
            final var module = (FusionModule) loader.loadClass("demo.Module").getConstructor().newInstance();
            final var beans = module.beans().toList();
            assertEquals(3, beans.size());
            assertTrue(beans.stream().allMatch(FunctionalBean.class::isInstance));
            assertEquals(List.of("demo.Envelope", "demo.Models$Leaf", "demo.Models$Mode"),
                    beans.stream().map(it -> ((java.lang.reflect.ParameterizedType) it.type())
                            .getActualTypeArguments()[0].getTypeName()).toList());
            final var model = loader.loadClass("demo.Envelope");
            try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true).register(module).start()) {
                final var codecs = new ArrayList<JsonCodec<?>>();
                for (final var bean : beans) {
                    final var type = assertInstanceOf(Types.ParameterizedTypeImpl.class, bean.type());
                    assertEquals(JsonCodec.class, type.getRawType());
                    try (final var instance = container.lookup(type)) {
                        codecs.add(assertInstanceOf(JsonCodec.class, instance.instance()));
                    }
                }
                try (final var mapper = new JsonMapperImpl(codecs, key -> Optional.empty())) {
                    final var json = "{\"mode\":\"yes\",\"n\\\"m\":\"name\",\"list\":[{\"value\":\"leaf\"}],\"count\":3,\"extra\":[true,2]}";
                    final var value = mapper.fromString(model, json);
                    assertEquals("{\"count\":3,\"extra\":[true,2],\"mode\":\"yes\",\"n\\\"m\":\"name\",\"list\":[{\"value\":\"leaf\"}]}", mapper.toString(value));
                    final var empty = mapper.fromString(model, "{}");
                    assertEquals("{\"count\":0}", mapper.toString(empty));
                    assertEquals("{\"count\":0}", mapper.toString(mapper.fromString(model, "{\"ignored\":9}")));
                    assertEquals("{\"count\":0}", mapper.toString(mapper.fromString(model, "{\"count\":null}")));
                    assertNull(mapper.fromString(model, "null"));
                    assertEquals("null", mapper.toString(null));
                    try (final var nulls = mapper.as(io.yupiik.fusion.json.JsonMapper.Configuring.class).orElseThrow().serializeNulls().build()) {
                        assertEquals("{\"boxed\":null,\"count\":0,\"mode\":null,\"n\\\"m\":null,\"list\":null}", nulls.toString(empty));
                    }
                }
            }
        }
        assertGeneratedClasses(2); // Bounded owners contain construction directly; no per-model codec or bean classes.
    }

    @Test
    void standaloneRecordFactoriesDoNotRequireBeansOrModule() throws Exception {
        compile(Map.of("demo.Leaf", """
                package demo;
                @io.yupiik.fusion.framework.build.api.json.JsonModel
                record Leaf(int value, Object any) {}
                """), "-Afusion.generateBeanForJsonCodec=false", "-Afusion.generateModule=false");
        try (final var loader = loader()) {
            final var codecs = standaloneCodecs(loader);
            assertEquals(1, codecs.size());
            try (final var mapper = new JsonMapperImpl(codecs, key -> Optional.empty())) {
                final var model = loader.loadClass("demo.Leaf");
                assertEquals(0, model.getAnnotations().length, "JsonModel is compiler metadata, not a runtime annotation");
                assertEquals("{\"value\":42}", mapper.toString(mapper.fromString(model, "{\"value\":42}")));
            }
        }
        assertGeneratedClasses(1);
        assertFalse(Files.exists(work.resolve("classes/demo/Module.class")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void manyModelsUseBoundedHoldersWithMeasuredDirectCallbackSites(final boolean beans) throws Exception {
        final var models = IntStream.range(0, 40).mapToObj(i ->
                "@io.yupiik.fusion.framework.build.api.json.JsonModel public record M" + i + "(String value) {}")
                .collect(joining("\n"));
        compile(Map.of("demo.Many", "package demo; public class Many {" + models + "}"), "-Afusion.generateModule=false",
                "-Afusion.generateBeanForJsonCodec=" + beans);
        try (final var loader = loader()) {
            final var codecs = standaloneCodecs(loader);
            assertEquals(40, codecs.size());
            assertTrue(GeneratedFactories.beans(loader).isEmpty());
            assertTrue(GeneratedFactories.methods(loader, "demo", FusionBean.class).isEmpty());
            try (final var mapper = new JsonMapperImpl(codecs, key -> Optional.empty())) {
                for (int i = 0; i < 40; i++) {
                    assertEquals("{\"value\":\"ok\"}", mapper.toString(mapper.fromString(loader.loadClass("demo.Many$M" + i), "{\"value\":\"ok\"}")));
                }
            }
        }
        assertGeneratedClasses(2);
        assertEquals(120, callbackSites(),
                "Each model has only getter, key-offset and constructor callbacks without a module");
    }

    @Test
    void ignoredAccessorsAreAbsentAndDuplicateKeysKeepTheLastValue() throws Exception {
        compile(Map.of("demo.Defaults", """
                package demo;
                import io.yupiik.fusion.framework.build.api.json.*;
                import java.util.*;
                @JsonModel public record Defaults(
                    String aa, String bb, @JsonIgnore boolean flag, @JsonIgnore int count,
                    @JsonIgnore long total, @JsonIgnore double ratio, @JsonIgnore Integer boxed,
                    @JsonIgnore Object object, @JsonIgnore List<String> list, @JsonIgnore Map<String, String> map) {
                    public Defaults {
                        if (flag || count != 0 || total != 0 || ratio != 0 || boxed != null || object != null || list != null || map != null) {
                            throw new AssertionError("Ignored constructor slots must retain defaults");
                        }
                    }
                    public boolean flag() { throw new AssertionError("Ignored accessor"); }
                    public int count() { throw new AssertionError("Ignored accessor"); }
                    public long total() { throw new AssertionError("Ignored accessor"); }
                    public double ratio() { throw new AssertionError("Ignored accessor"); }
                    public Integer boxed() { throw new AssertionError("Ignored accessor"); }
                    public Object object() { throw new AssertionError("Ignored accessor"); }
                    public List<String> list() { throw new AssertionError("Ignored accessor"); }
                    public Map<String, String> map() { throw new AssertionError("Ignored accessor"); }
                }
                """), "-Afusion.generateModule=false", "-Afusion.generateBeanForJsonCodec=false");
        try (final var loader = loader(); final var mapper = new JsonMapperImpl(standaloneCodecs(loader), key -> Optional.empty())) {
            final var model = loader.loadClass("demo.Defaults");
            final var input = "{\"aa\":\"first\",\"bb\":\"second\",\"aa\":\"last\",\"bb\":null," +
                    "\"flag\":true,\"count\":9,\"total\":9,\"ratio\":9,\"boxed\":9,\"object\":{},\"list\":[\"x\"],\"map\":{\"x\":\"y\"}}";
            final var value = mapper.fromString(model, input);
            assertEquals("{\"aa\":\"last\"}", mapper.toString(value));
            try (final var nulls = mapper.as(io.yupiik.fusion.json.JsonMapper.Configuring.class).orElseThrow().serializeNulls().build()) {
                assertEquals("{\"aa\":\"last\",\"bb\":null}", nulls.toString(value));
            }
        }
        assertGeneratedClasses(1);
        assertEquals(4, callbackSites(), "Only two active getters, key-offset and constructor callbacks are emitted");
        try (final var files = Files.list(work.resolve("generated/demo"))) {
            final var implementations = new ArrayList<String>();
            for (final var file : files.filter(it -> it.toString().endsWith(".java")).toList()) {
                final var text = Files.readString(file);
                if (text.contains("new io.yupiik.fusion.json.serialization.MetadataJsonCodec<demo.Defaults>")) {
                    implementations.add(text);
                }
            }
            assertEquals(1, implementations.size(), "Exactly one standalone codec construction owner without a module");
            final var source = implementations.get(0);
            assertTrue(source.contains("demo.Defaults::aa"), source);
            assertTrue(source.contains("demo.Defaults::bb"), source);
            assertFalse(source.contains("demo.Defaults::flag"), source);
            assertFalse(source.contains("demo.Defaults::object"), source);
        }
    }

    @Test
    void emptyAndIgnoredOnlyRecordsNeedNoAccessorCallbacks() throws Exception {
        compile(Map.of("demo.Models", """
                package demo;
                import io.yupiik.fusion.framework.build.api.json.*;
                public class Models {
                    @JsonModel public record Empty() {}
                    @JsonModel public record Ignored(@JsonIgnore int count) {
                        public Ignored {
                            if (count != 0) { throw new AssertionError("Ignored slot"); }
                        }
                        public int count() { throw new AssertionError("Ignored accessor"); }
                    }
                }
                """), "-Afusion.generateModule=false", "-Afusion.generateBeanForJsonCodec=false");
        try (final var loader = loader(); final var mapper = new JsonMapperImpl(standaloneCodecs(loader), key -> Optional.empty())) {
            for (final var name : List.of("Empty", "Ignored")) {
                final var model = loader.loadClass("demo.Models$" + name);
                assertEquals("{}", mapper.toString(mapper.fromString(model, "{\"count\":99,\"nested\":[{}]}")));
            }
        }
        assertGeneratedClasses(1);
        assertEquals(4, callbackSites(), "Each model needs only key-offset and constructor callbacks");
    }

    @Test
    void typedContainerAndScalarMetadataUsesTheExistingRuntimeEngine() throws Exception {
        compile(Map.of("demo.Shapes", """
                package demo;
                import io.yupiik.fusion.framework.build.api.json.*;
                import java.util.*;
                @JsonModel public record Shapes(
                    Set<Leaf> set, Map<String, List<Leaf>> groups, Map<String, Leaf> index,
                    java.math.BigDecimal decimal, java.time.LocalDate date,
                    boolean flag, long total, double ratio, Boolean boxed) {
                    @JsonModel public record Leaf(String value) {}
                }
                """), "-Afusion.generateBeanForJsonCodec=false", "-Afusion.generateModule=false");
        try (final var loader = loader(); final var mapper = new JsonMapperImpl(standaloneCodecs(loader), key -> Optional.empty())) {
            final var model = loader.loadClass("demo.Shapes");
            final var json = "{\"boxed\":true,\"date\":\"2026-11-22\",\"decimal\":\"1.25\",\"flag\":true," +
                    "\"groups\":{\"a\":[{\"value\":\"group\"}]},\"index\":{\"a\":{\"value\":\"index\"}}," +
                    "\"ratio\":1.5,\"set\":[{\"value\":\"set\"}],\"total\":42}";
            assertEquals(json, mapper.toString(mapper.fromString(model, json)));
            assertEquals("{\"flag\":false,\"ratio\":0.0,\"total\":0}", mapper.toString(mapper.fromString(model, "{}")));
        }
        assertGeneratedClasses(1);
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void enumStandaloneFactoryUsesSharedConversionCallback(final boolean schemas, final boolean beans) throws Exception {
        compile(Map.of("demo.Mode", """
                package demo;
                @io.yupiik.fusion.framework.build.api.json.JsonModel
                public enum Mode { ONE; public String toJsonString() { return "custom"; } }
                """), "-Afusion.generateModule=false", "-Afusion.generateBeanForJsonCodec=" + beans,
                "-Afusion.generateJsonSchemas=" + schemas, "-Afusion.generatePartialOpenRPC=false");
        final var schema = work.resolve("classes/META-INF/fusion/json/schemas.json");
        assertEquals(schemas, Files.exists(schema));
        if (schemas) {
            assertTrue(Files.readString(schema).contains("ONE"));
        }
        try (final var loader = loader(); final var mapper = new JsonMapperImpl(standaloneCodecs(loader), key -> Optional.empty())) {
            assertEquals("\"custom\"", mapper.toString(mapper.fromString(loader.loadClass("demo.Mode"), "\"custom\"")));
            assertThrows(IllegalArgumentException.class, () -> mapper.fromString(loader.loadClass("demo.Mode"), "\"ONE\""));
            assertTrue(GeneratedFactories.beans(loader).isEmpty());
            assertTrue(GeneratedFactories.methods(loader, "demo", FusionBean.class).isEmpty(),
                    "Disabling the module omits descriptor factories even when codec beans are enabled");
        }
        assertGeneratedClasses(0); // A single construction owner contains the direct enum factory.
        assertEquals(1, callbackSites(), "Only enum conversion remains without a module");
        assertFalse(Files.exists(work.resolve("classes/demo/Module.class")));
        assertFalse(Files.exists(work.resolve("classes/META-INF/fusion/json/models-v1")));
    }

    @Test
    void defaultPackageEnumHasStandaloneBindingAndUnprefixedSchemaKey() throws Exception {
        compile(Map.of("Mode", "@io.yupiik.fusion.framework.build.api.json.JsonModel public enum Mode { ONE }"),
                "-Afusion.generateBeanForJsonCodec=false", "-Afusion.generateModule=false");
        try (final var loader = loader(); final var mapper = new JsonMapperImpl(
                List.of(GeneratedFactories.invoke(loader, "", "Mode$FusionJsonCodec", JsonCodec.class)), key -> Optional.empty())) {
            assertEquals("\"ONE\"", mapper.toString(mapper.fromString(loader.loadClass("Mode"), "\"ONE\"")));
        }
        final var schemas = Files.readString(work.resolve("classes/META-INF/fusion/json/schemas.json"));
        assertTrue(schemas.contains("\"Mode\""), schemas);
        assertFalse(schemas.contains("\".Mode\""), schemas);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nativeResourcesIncludeRuntimeProvidersAndExactSchemaAndOpenRpcPaths(final boolean customPaths) throws Exception {
        final var schemas = customPaths ? "application/schema-bundle.json" : "META-INF/fusion/json/schemas.json";
        final var openrpc = customPaths ? "application/api.json" : "META-INF/fusion/jsonrpc/openrpc.json";
        compile(Map.of("demo.Api", """
                package demo;
                public class Api {
                    @io.yupiik.fusion.framework.build.api.jsonrpc.JsonRpc("model")
                    public Model model() { return null; }
                    @io.yupiik.fusion.framework.build.api.json.JsonModel
                    public record Model(String value) {}
                }
                """, "demo.Config", """
                package demo;
                @io.yupiik.fusion.framework.build.api.configuration.RootConfiguration("test")
                public record Config(String value) {}
                """), "-Afusion.generateModule=false", "-Afusion.generateBeanForJsonCodec=false",
                "-Afusion.generateBeanForJsonRpcEndpoints=false", "-Afusion.generateBeanForRootConfiguration=false",
                "-Afusion.generateJsonSchemas=" + schemas, "-Afusion.generatePartialOpenRPC=" + openrpc);
        assertEquals(List.of(schemas, openrpc,
                "META-INF/services/io.yupiik.fusion.framework.api.container.FusionModule").stream().sorted().toList(), nativeResourceGlobs());
        assertTrue(Files.exists(work.resolve("classes/" + schemas)));
        assertTrue(Files.exists(work.resolve("classes/" + openrpc)));
        assertTrue(Files.exists(work.resolve("classes/META-INF/fusion/configuration/documentation.json")));
        assertFalse(Files.exists(work.resolve("classes/META-INF/fusion/json/models-v1")));
        try (final var loader = loader(); final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            assertInstanceOf(Map.class, mapper.fromString(Object.class, Files.readString(Path.of(loader.getResource(schemas).toURI()))));
            assertInstanceOf(Map.class, mapper.fromString(Object.class, Files.readString(Path.of(loader.getResource(openrpc).toURI()))));
        }
    }

    @Test
    void disabledSchemasAndOpenRpcAreNotRegisteredAsNativeResources() throws Exception {
        compile(Map.of("demo.Model", "package demo; @io.yupiik.fusion.framework.build.api.json.JsonModel public record Model(String value) {}"),
                "-Afusion.generateModule=false", "-Afusion.generateBeanForJsonCodec=false",
                "-Afusion.generateJsonSchemas=false", "-Afusion.generatePartialOpenRPC=false");
        assertEquals(List.of("META-INF/services/io.yupiik.fusion.framework.api.container.FusionModule"), nativeResourceGlobs());
        assertFalse(Files.exists(work.resolve("classes/META-INF/fusion/json/models-v1")));
    }

    private List<String> nativeResourceGlobs() throws Exception {
        try (final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            final var metadata = assertInstanceOf(Map.class, mapper.fromString(Object.class,
                    Files.readString(work.resolve("classes/META-INF/native-image/demo/Module__fusion/reachability-metadata.json"))));
            final List<?> resources = assertInstanceOf(List.class, metadata.get("resources"));
            return resources.stream().map(it -> assertInstanceOf(Map.class, it).get("glob").toString()).sorted().toList();
        }
    }

    @Test
    void jsonRpcGenerationDoesNotRequireOptionalSchemaDocuments() throws Exception {
        compile(Map.of("demo.Api", """
                package demo;
                public class Api {
                    @io.yupiik.fusion.framework.build.api.jsonrpc.JsonRpc("model")
                    public Model model(Model input) { return input; }
                    @io.yupiik.fusion.framework.build.api.json.JsonModel
                    public record Model(String value) {}
                }
                """), "-Afusion.generateModule=false", "-Afusion.generateJsonSchemas=false");
        assertFalse(Files.exists(work.resolve("classes/META-INF/fusion/json/schemas.json")));
        assertTrue(Files.readString(work.resolve("classes/META-INF/fusion/jsonrpc/openrpc.json")).contains("model"));
        assertEquals(List.of("META-INF/fusion/jsonrpc/openrpc.json",
                "META-INF/services/io.yupiik.fusion.framework.api.container.FusionModule"), nativeResourceGlobs());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void explicitNativeGenerationOptionIsHonored(final boolean enabled) throws Exception {
        compile(Map.of("demo.Model", "package demo; @io.yupiik.fusion.framework.build.api.json.JsonModel public record Model(String value) {}"),
                "-Afusion.generateModule=false", "-Afusion.generateNativeImage=" + enabled,
                "-Afusion.generateJsonSchemas=application/schema*.json", "-Afusion.generatePartialOpenRPC=false");
        assertTrue(Files.exists(work.resolve("classes/application/schema*.json")));
        assertEquals(enabled, Files.exists(work.resolve("classes/META-INF/native-image/demo/Module__fusion/reachability-metadata.json")));
        if (enabled) {
            assertEquals(List.of("META-INF/services/io.yupiik.fusion.framework.api.container.FusionModule",
                    "application/schema\\*.json"), nativeResourceGlobs());
        }
    }

    private List<JsonCodec<?>> standaloneCodecs(final ClassLoader loader) throws Exception {
        final var result = new ArrayList<JsonCodec<?>>();
        for (final var method : GeneratedFactories.methods(loader, "demo", JsonCodec.class)) {
            result.add(GeneratedFactories.invoke(loader, "demo", method.getName(), JsonCodec.class));
        }
        return result;
    }

    private void assertGeneratedClasses(final int count) throws Exception {
        try (final var files = Files.list(work.resolve("generated/demo"))) {
            final var sources = files.toList();
            assertTrue(sources.size() > 0 && sources.size() <= count + 1,
                    "Construction owners must remain bounded: " + sources);
            for (final var source : sources) {
                final var name = source.getFileName().toString().replace(".java", "");
                assertFalse(name.contains("FusionJsonCodec"), name);
                final var bytes = Files.readAllBytes(work.resolve("classes/demo/" + name + ".class"));
                final var constants = new String(bytes, StandardCharsets.ISO_8859_1);
                assertFalse(constants.contains("/Dispatch"), name);
                final var bytecode = bytecode(name);
                assertFalse(bytecode.contains("Dispatch.invoke"), bytecode);
                assertFalse(bytecode.contains("DispatchBindings"), bytecode);
                assertFalse(bytecode.contains("EnumConversion"), bytecode);
                assertFalse(constants.contains("java/lang/reflect/Method"), name);
            }
        }
        assertFalse(Files.exists(work.resolve("generated/demo/FusionFactories.java")));
        try (final var files = Files.list(work.resolve("classes/demo"))) {
            final var generated = files.filter(it -> {
                final var name = it.getFileName().toString();
                return name.startsWith("FusionBindings") || name.equals("FusionFactories.class") || name.startsWith("Module") || name.contains("FusionJsonCodec") || name.contains("FusionBean");
            }).toList();
            assertTrue(generated.size() > 0 && generated.size() <= count + 1, generated.toString());
        }
    }

    private int callbackSites() throws Exception {
        int sites = 0;
        try (final var files = Files.list(work.resolve("generated/demo"))) {
            for (final var source : files.toList()) {
                final var name = source.getFileName().toString().replace(".java", "");
                final var bytecode = bytecode(name);
                final var lambdas = LAMBDA_BOOTSTRAP.matcher(bytecode).results().map(it -> it.group(1)).toList();
                sites += (int) DYNAMIC_INSTRUCTION.matcher(bytecode).results().filter(it -> lambdas.contains(it.group(1))).count();
            }
        }
        return sites;
    }

    private String bytecode(final String name) throws Exception {
        final var output = new StringWriter();
        final var javap = java.util.spi.ToolProvider.findFirst("javap").orElseThrow();
        assertEquals(0, javap.run(new java.io.PrintWriter(output), new java.io.PrintWriter(output),
                "-c", "-p", "-v", work.resolve("classes/demo/" + name + ".class").toString()), output.toString());
        return output.toString();
    }

    private URLClassLoader loader() throws Exception {
        return new URLClassLoader(new URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader());
    }

    private void compile(final Map<String, String> sources, final String... extra) throws Exception {
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
            final var options = new ArrayList<>(List.of("--release", "17", "-classpath", System.getProperty("java.class.path"),
                    "-d", work.resolve("classes").toString(), "-s", work.resolve("generated").toString(),
                    "-Afusion.workdir=" + work.resolve("state"), "-Afusion.moduleFqn=demo.Module",
                    "-Xlint:all,-processing,-serial", "-Werror"));
            options.addAll(List.of(extra));
            final var task = compiler.getTask(output, manager, null, options, null, manager.getJavaFileObjectsFromPaths(paths));
            task.setProcessors(List.of(new FusionProcessor()));
            assertTrue(task.call(), output.toString());
        }
    }
}
