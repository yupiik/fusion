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
import io.yupiik.fusion.cli.internal.BaseCliCommand;
import io.yupiik.fusion.http.server.spi.DirectEndpointHandler;
import java.util.function.Function;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.container.bean.ProvidedInstanceBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.processor.test.GeneratedFactories;
import io.yupiik.fusion.framework.api.container.bean.FunctionalBean;
import io.yupiik.fusion.http.server.api.Body;
import io.yupiik.fusion.http.server.api.Cookie;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.impl.DefaultEndpoint;
import io.yupiik.fusion.http.server.impl.flow.BytesPublisher;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.serialization.JsonCodec;
import io.yupiik.fusion.jsonrpc.JsonRpcException;
import io.yupiik.fusion.jsonrpc.impl.DefaultJsonRpcMethod;
import io.yupiik.fusion.jsonrpc.impl.JsonRpcMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CompactServiceProcessorTest {
    @TempDir
    Path work;

    private static final String APP = """
            package demo;
            import io.yupiik.fusion.framework.api.scope.*;
            import io.yupiik.fusion.framework.build.api.http.*;
            import io.yupiik.fusion.framework.build.api.jsonrpc.*;
            import io.yupiik.fusion.framework.build.api.cli.*;
            import io.yupiik.fusion.framework.build.api.configuration.*;
            import io.yupiik.fusion.framework.build.api.order.*;
            import io.yupiik.fusion.http.server.api.*;
            import java.util.*;
            import java.util.concurrent.*;
            @DefaultScoped
            public class App {
                @HttpMatcher(methods={"POST", "PUT"}, path="/echo/(.*)", pathMatching=HttpMatcher.PathMatching.REGEX, priority=31)
                @Order(71)
                public CompletionStage<String> echo(Request request, String value) { return CompletableFuture.completedStage(value + "!"); }
                @HttpMatcher(path="/direct", pathMatching=HttpMatcher.PathMatching.EXACT) public Response direct() { throw new IllegalStateException("direct"); }
                @HttpMatcher(path="/body", pathMatching=HttpMatcher.PathMatching.EXACT) public Response body(String value) { throw new IllegalStateException("body"); }
                @HttpJavaMatcher("req.path().equals(root.path())")
                public Response java(Request request) { return Response.of().status(202).build(); }
                public String path() { return "/java"; }
                @JsonRpc("convert")
                public String convert(Request request, @JsonRpcParam(value="count", required=true, documentation="count doc") int count,
                        List<Long> numbers, Map<String, Integer> values, Optional<Boolean> flag) {
                    return request.path() + ":" + count + ":" + numbers + ":" + values + ":" + flag;
                }
                @JsonRpc("notify") public void notify(Request request) {}
                @JsonRpc("asyncNotify") public CompletionStage<Void> asyncNotify() { return CompletableFuture.completedStage(null); }
                @RootConfiguration("deploy.run") public record Conf(@Property(required=true, documentation="name doc") String name) {}
                @DefaultScoped public static class Dep implements AutoCloseable {
                    public static String trace = "";
                    public Dep() { trace += "lookup;"; }
                    @io.yupiik.fusion.framework.build.api.lifecycle.Destroy public void close() { trace += "close;"; }
                }
                @Command(name={"deploy", "run"}, description="deploy description")
                public static class Run implements Runnable {
                    private final Conf conf;
                    public Run(Conf conf, Dep dep) { this.conf = conf; }
                    public void run() { Dep.trace += conf.name() + ";"; }
                }
            }
            """;

    @Test
    void cliDependencyClassLiteralsUseErasedTypes() throws Exception {
        compile("""
                package demo;
                public class App {
                    public static String result;
                    @io.yupiik.fusion.framework.build.api.configuration.RootConfiguration("options")
                    public record Options() {}
                    @io.yupiik.fusion.framework.build.api.cli.Command(name="run", description="injected list")
                    public static class Run implements Runnable {
                        private final java.util.List<?> values;
                        public Run(Options options, java.util.List<?> values) { this.values = values; }
                        public void run() { result = values.toString(); }
                    }
                }
                """, "-Werror");
        try (final var loader = loader();
             final var container = ConfiguringContainer.of().disableAutoDiscovery(true)
                     .register(new ProvidedInstanceBean<>(DefaultScoped.class, List.class, () -> List.of("injected"))).start()) {
            final var command = commands(loader).get(0).apply(container);
            try (final var instance = command.create(key -> Optional.empty(), new ArrayList<>())) {
                instance.instance().run();
            }
            assertEquals("[injected]", loader.loadClass("demo.App").getField("result").get(null));
        }
    }

    @Test
    void rpcConversionsDoNotResolveJdkTypesFromTheBusinessPackage() throws Exception {
        compile("""
                package demo;
                class Number {}
                class Boolean {}
                class String {}
                public class App {
                    @io.yupiik.fusion.framework.build.api.jsonrpc.JsonRpc("shadowed")
                    public java.lang.String shadowed(int count, boolean enabled, java.lang.Boolean boxed,
                            java.lang.String label, java.util.List<java.lang.Boolean> flags,
                            java.util.Map<java.lang.String, java.lang.Integer> counts,
                            java.util.Optional<java.lang.Boolean> optional) {
                        return count + ":" + enabled + ":" + boxed + ":" + label + ":" + flags + ":" + counts + ":" + optional;
                    }
                }
                """, "-Werror");
        try (final var loader = loader(); final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            final var root = loader.loadClass("demo.App").getConstructor().newInstance();
            final var method = methods(loader, root, mapper).get(0);
            assertEquals("7:true:false:text:[true, false]:{a=2}:Optional[true]", method.invoke(new JsonRpcMethod.Context(null,
                    List.of(7, true, false, "text", List.of(true, false), Map.of("a", 2), true))).toCompletableFuture().join());
            final var nullable = new ArrayList<>(List.of(7, true, false, "text", List.of(true, false), Map.of("a", 2), true));
            nullable.set(2, null);
            nullable.set(6, null);
            assertEquals("7:true:null:text:[true, false]:{a=2}:Optional.empty", method.invoke(
                    new JsonRpcMethod.Context(null, nullable)).toCompletableFuture().join());
        }
    }

    @Test
    void rpcNumericConversionsPreserveUpstreamNumberSupport() throws Exception {
        compile("""
                package demo;
                public class App {
                    @io.yupiik.fusion.framework.build.api.jsonrpc.JsonRpc("numbers")
                    public String numbers(float primitive, Double boxed, Number number,
                            java.util.List<Float> list, java.util.Map<String, Double> map,
                            java.util.Optional<Number> optional) {
                        return primitive + ":" + boxed + ":" + number + ":" + list + ":" + map + ":" + optional;
                    }
                }
                """, "-Werror");
        try (final var loader = loader(); final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            final var root = loader.loadClass("demo.App").getConstructor().newInstance();
            final var method = methods(loader, root, mapper).get(0);
            final var params = mapper.fromString(Object.class, "[1.5,2.5,3.5,[4.5],{\"value\":5.5},6.5]");
            assertEquals("1.5:2.5:3.5:[4.5]:{value=5.5}:Optional[6.5]", method.invoke(
                    new JsonRpcMethod.Context(null, params)).toCompletableFuture().join());
            final var nullable = mapper.fromString(Object.class, "[null,null,null,[null],{},null]");
            assertEquals("0.0:null:null:[null]:{}:Optional.empty", method.invoke(
                    new JsonRpcMethod.Context(null, nullable)).toCompletableFuture().join());
        }
    }

    @Test
    void directFactoriesAreHiddenButNamedArtifactsStayBounded() throws Exception {
        compile(APP);
        try (final var loader = loader()) {
            final var factories = new ArrayList<Object>();
            for (final var method : GeneratedFactories.methods(loader, "demo", Function.class)) {
                factories.add(GeneratedFactories.invoke(loader, "demo", method.getName(), Function.class));
            }
            assertEquals(8, factories.size());
            assertTrue(factories.stream().allMatch(factory -> factory.getClass().isHidden()));
            assertEquals(8, factories.stream().map(Object::getClass).distinct().count());
            try (final var files = Files.list(work.resolve("classes/demo"))) {
                final var generated = files.filter(p -> p.getFileName().toString().startsWith("FusionBindings") ||
                        p.getFileName().toString().equals("Module.class")).toList();
                assertTrue(generated.size() > 0 && generated.size() <= 3, generated.toString());
            }
        }
        assertCompactOutput();
    }

    @Test
    void asynchronousHttpSerializationRetainsOnlyDirectBusinessCallbacks() throws Exception {
        compile(APP.replace("public class App {", "public class App { public final CompletableFuture<String> pending = new CompletableFuture<>();")
                .replace("return CompletableFuture.completedStage(value + \"!\");", "return pending;"));
        try (final var loader = loader(); final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            final var root = loader.loadClass("demo.App").getConstructor().newInstance();
            final var echo = endpoints(loader, root, mapper).stream().filter(e -> e.priority() == 31).findFirst().orElseThrow();
            final var response = echo.handle(new TestRequest("POST", "/echo/async", "\"value\"", "reactive")).toCompletableFuture();
            assertFalse(response.isDone());
            @SuppressWarnings("unchecked")
            final var pending = (CompletableFuture<String>) root.getClass().getField("pending").get(root);
            pending.complete("resolved");
            assertEquals("\"resolved\"", text(response.join()));

            final var failingRoot = loader.loadClass("demo.App").getConstructor().newInstance();
            final var failingEcho = endpoints(loader, failingRoot, mapper).stream().filter(e -> e.priority() == 31).findFirst().orElseThrow();
            final var failed = failingEcho.handle(new TestRequest("POST", "/echo/async", "\"value\"", "reactive")).toCompletableFuture();
            @SuppressWarnings("unchecked")
            final var failure = (CompletableFuture<String>) failingRoot.getClass().getField("pending").get(failingRoot);
            final var cause = new IllegalStateException("business failure");
            failure.completeExceptionally(cause);
            assertSame(cause, assertThrows(CompletionException.class, failed::join).getCause());
        }
        assertCompactOutput();
    }

    @Test
    void httpRpcAndCliUseOnlySharedRuntimeClasses() throws Exception {
        compile(APP);
        try (final var loader = loader(); final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            final var module = (FusionModule) loader.loadClass("demo.Module").getConstructor().newInstance();
            final var beans = module.beans().toList();
            assertTrue(beans.stream().allMatch(FunctionalBean.class::isInstance));
            assertEquals(List.of("demo.App$Conf", "demo.App$Dep", "demo.App",
                            "io.yupiik.fusion.cli.internal.CliCommand<demo.App$Run>",
                            "io.yupiik.fusion.jsonrpc.impl.DefaultJsonRpcMethod",
                            "io.yupiik.fusion.http.server.impl.DefaultEndpoint",
                            "io.yupiik.fusion.jsonrpc.impl.DefaultJsonRpcMethod",
                            "io.yupiik.fusion.http.server.impl.DefaultEndpoint",
                            "io.yupiik.fusion.http.server.impl.DefaultEndpoint",
                            "io.yupiik.fusion.http.server.impl.DefaultEndpoint",
                            "io.yupiik.fusion.jsonrpc.impl.DefaultJsonRpcMethod"),
                    beans.stream().map(bean -> bean.type().getTypeName()).toList());
            final var endpointBean = beans.stream().filter(bean -> bean.type() == DefaultEndpoint.class && bean.priority() == 71)
                    .findFirst().orElseThrow();
            assertEquals(DefaultEndpoint.class, endpointBean.type());
            assertEquals(71, endpointBean.priority());
            try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true).register(module).start()) {
                final var dependencies = new ArrayList<Instance<?>>();
                try {
                    // Resolve the owner through the ordinary container, but keep mapper ownership local to this test.
                    final var owner = container.lookup(loader.loadClass("demo.App"));
                    dependencies.add(owner);
                    final var endpoints = endpoints(loader, owner.instance(), mapper);
                    final var echo = endpoints.stream().filter(e -> e.priority() == 31).findFirst().orElseThrow();
                    for (final var mode : List.of("stream", "reader", "both", "reactive")) {
                        final var request = new TestRequest("pOsT", "/echo/here", "\"h\u00e9llo\"", mode);
                        assertTrue(echo.matches(request));
                        final var matcher = request.attribute("fusion.http.matcher", Matcher.class);
                        assertEquals("here", matcher.group(1));
                        final var response = echo.handle(request).toCompletableFuture().join();
                        assertEquals(200, response.status());
                        assertEquals(List.of("application/json;charset=utf-8"), response.headers().get("content-type"));
                        assertEquals("\"h\u00e9llo!\"", text(response));
                    }
                    assertFalse(echo.matches(new TestRequest("GET", "/echo/here", "", "stream")));
                    assertFalse(echo.matches(new TestRequest("POST", null, "", "stream")));
                    final var java = endpoints.stream().filter(e -> e.matches(new TestRequest("GET", "/java", "", "stream"))).findFirst().orElseThrow();
                    assertEquals(202, java.handle(new TestRequest("GET", "/java", "", "stream")).toCompletableFuture().join().status());
                    final var direct = route(endpoints, "/direct");
                    assertEquals("direct", assertThrows(IllegalStateException.class, () -> direct.handle(null)).getMessage());
                    final var body = route(endpoints, "/body");
                    final var failure = body.handle(new TestRequest("GET", "/body", "\"x\"", "stream"));
                    assertEquals("body", assertThrows(CompletionException.class, () -> failure.toCompletableFuture().join()).getCause().getMessage());

                    final var methods = methods(loader, owner.instance(), mapper);
                    final var convert = methods.stream().filter(m -> m.name().equals("convert")).findFirst().orElseThrow();
                    final var request = new TestRequest("POST", "/rpc", "", "stream");
                    final var args = List.of(2, List.of(3, 4), Map.of("a", 5), true);
                    assertEquals("/rpc:2:[3, 4]:{a=5}:Optional[true]", convert.invoke(new JsonRpcMethod.Context(request, args)).toCompletableFuture().join());
                    assertEquals("/rpc:2:[3, 4]:{a=5}:Optional[true]", convert.invoke(new JsonRpcMethod.Context(request,
                            Map.of("count", 2, "numbers", List.of(3, 4), "values", Map.of("a", 5), "flag", true))).toCompletableFuture().join());
                    final var required = assertThrows(JsonRpcException.class, () -> convert.invoke(new JsonRpcMethod.Context(request, List.of())));
                    assertEquals(-32601, required.code());
                    assertEquals("Missing 'count' parameter.", required.getMessage());
                    assertEquals(-32700, assertThrows(JsonRpcException.class, () -> convert.invoke(new JsonRpcMethod.Context(request, "bad"))).code());
                    assertTrue(methods.stream().filter(m -> m.name().endsWith("otify")).allMatch(DefaultJsonRpcMethod::isNotification));

                    final var commandBean = beans.stream().filter(b -> b.type().getTypeName().equals(
                            "io.yupiik.fusion.cli.internal.CliCommand<demo.App$Run>")).findFirst().orElseThrow();
                    assertEquals("io.yupiik.fusion.cli.internal.CliCommand<demo.App$Run>", commandBean.type().getTypeName());
                    final var registeredCommand = assertInstanceOf(CliCommand.class, commandBean.create(container, new ArrayList<>()));
                    assertEquals("deploy/run", registeredCommand.name());
                    try (final var descriptor = container.lookup(CliCommand.class)) {
                        assertEquals("deploy/run", descriptor.instance().name());
                    }
                    final var command = commands(loader).get(0).apply(container);
                    assertEquals(BaseCliCommand.class, command.getClass());
                    assertEquals("deploy/run", command.name());
                    assertEquals("--deploy-run-name", command.keyMapper().apply("deploy.run.name"));
                    final var dep = loader.loadClass("demo.App$Dep");
                    assertEquals("", dep.getField("trace").get(null));
                    for (int i = 0; i < 2; i++) {
                        try (final var instance = command.create(key -> Optional.of("value"), new ArrayList<>())) {
                            instance.instance().run();
                        }
                    }
                    assertEquals("lookup;value;close;lookup;value;close;", dep.getField("trace").get(null));
                    new io.yupiik.fusion.cli.CliAwaiter(new io.yupiik.fusion.framework.api.main.Args(List.of("deploy", "run", "--name", "short")),
                            key -> Optional.empty(), List.of(command)).await();
                    assertEquals("lookup;value;close;lookup;value;close;lookup;short;close;", dep.getField("trace").get(null));
                    final var missing = assertThrows(io.yupiik.fusion.framework.api.configuration.MissingRequiredParameterException.class,
                            () -> command.create(key -> Optional.empty(), new ArrayList<>()));
                    assertTrue(missing.getMessage().contains("--deploy-run-name"), missing.getMessage());
                    assertTrue(missing.getMessage().contains("Available parameters:"), missing.getMessage());
                } finally {
                    for (int i = dependencies.size() - 1; i >= 0; i--) {
                        dependencies.get(i).close();
                    }
                }
            }
        }
        assertCompactOutput();
    }

    @Test
    void standaloneFactoriesSurviveDisabledBeanAndModuleGeneration() throws Exception {
        compile(APP.replace("public Run(Conf conf, Dep dep)", "public Run(Conf conf)"),
                "-Afusion.generateModule=false", "-Afusion.generateBeanForHttpEndpoints=false",
                "-Afusion.generateBeanForJsonRpcEndpoints=false", "-Afusion.generateBeanForCliCommands=false");
        try (final var loader = loader(); final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            final var root = loader.loadClass("demo.App").getConstructor().newInstance();
            assertEquals(4, endpoints(loader, root, mapper).size());
            assertEquals(3, methods(loader, root, mapper).size());
            assertEquals(1, commands(loader).size());
            assertEquals("deploy/run", commands(loader).get(0).apply(null).name());
        }
        try (final var sources = Files.walk(work.resolve("generated"))) {
            for (final var source : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                final var text = Files.readString(source);
                for (final var suffix : List.of("$FusionHttpEndpoint.FusionBean", "$FusionHttpJavaEndpoint.FusionBean", "$FusionJsonRpcMethod.FusionBean", "$FusionCliCommand.FusionBean")) {
                    assertFalse(text.contains(suffix), text);
                }
            }
        }
        assertCompactOutput();
    }

    @Test
    void javaMatcherPreservesMultistatementBlockBody() throws Exception {
        compile(APP.replace("req.path().equals(root.path())",
                "  { final var path = req.path(); if (path == null) { return false; } return path.equals(root.path()); }  "));
        try (final var loader = loader(); final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            final var root = loader.loadClass("demo.App").getConstructor().newInstance();
            final var endpoint = route(endpoints(loader, root, mapper), "/java");
            assertFalse(endpoint.matches(new TestRequest("GET", null, "", "stream")));
            assertFalse(endpoint.matches(new TestRequest("GET", "/other", "", "stream")));
            assertEquals(202, endpoint.handle(new TestRequest("GET", "/java", "", "stream")).toCompletableFuture().join().status());
        }
        assertCompactOutput();
    }

    @Test
    void rpcMapperConvertedMapsPreserveNullAndNestedValues() throws Exception {
        compile("""
                package demo;
                import io.yupiik.fusion.framework.build.api.jsonrpc.*;
                import io.yupiik.fusion.framework.build.api.json.JsonModel;
                import java.util.*;
                public class App {
                    @JsonModel public record Child(String name) {}
                    @JsonModel public record Payload(String name, Map<String,Child> children, List<Child> items) {}
                    @JsonRpc("models") public String models(Map<String,Payload> values) {
                        return values.containsKey("nil") + ":" + values.get("nil");
                    }
                    @JsonRpc("nested") public String nested(Map<String,Payload> values) {
                        final var model = values.get("model");
                        return values.containsKey("nil") + ":" + values.get("nil") + ":" + model.children().get("child").name()
                            + ":" + model.children().containsKey("nil") + ":" + model.children().get("nil")
                            + ":" + model.items().get(0) + ":" + model.items().get(1).name();
                    }
                    @JsonRpc("doubles") public String doubles(Map<String,Double> values) {
                        return values.containsKey("nil") + ":" + values.get("nil");
                    }
                    @JsonRpc("objects") public String objects(Map<String,Object> values) { return "unexpected"; }
                    @JsonRpc("booleans") public String booleans(Map<String,Boolean> values) { return "unexpected"; }
                    @JsonRpc("integers") public String integers(Map<String,Integer> values) { return "unexpected"; }
                    @JsonRpc("longs") public String longs(Map<String,Long> values) { return "unexpected"; }
                    @JsonRpc("strings") public String strings(Map<String,String> values) { return "unexpected"; }
                }
                """);
        try (final var loader = loader(); final var mapper = new JsonMapperImpl(
                List.of((JsonCodec<?>) GeneratedFactories.invoke(loader, "demo", "App$Payload$FusionJsonCodec", JsonCodec.class),
                        (JsonCodec<?>) GeneratedFactories.invoke(loader, "demo", "App$Child$FusionJsonCodec", JsonCodec.class)), key -> Optional.empty())) {
            final var root = loader.loadClass("demo.App").getConstructor().newInstance();
            final var nil = new HashMap<String, Object>();
            nil.put("nil", null);
            final var nested = new HashMap<>(nil);
            final var children = new HashMap<>(nil);
            children.put("child", Map.of("name", "nested"));
            final var items = new ArrayList<Object>();
            items.add(null);
            items.add(Map.of("name", "item"));
            nested.put("model", Map.of("children", children, "items", items));
            for (final var method : methods(loader, root, mapper)) {
                final var params = Map.of("values", method.name().equals("nested") ? nested : nil);
                final var context = new JsonRpcMethod.Context(null, params);
                switch (method.name()) {
                    case "models" -> assertEquals("true:null", method.invoke(context).toCompletableFuture().join());
                    case "doubles" -> assertThrows(NullPointerException.class, () -> method.invoke(context));
                    case "nested" -> assertEquals("true:null:nested:true:null:null:item", method.invoke(context).toCompletableFuture().join());
                    default -> assertThrows(NullPointerException.class, () -> method.invoke(context), method.name());
                }
            }
        }
        assertCompactOutput();
    }

    @Test
    void javaMatcherKeepsExplicitUserLambdaAndLexicalRequest() throws Exception {
        compile(APP.replace("req.path().equals(root.path())", "req.path().chars().anyMatch(c -> c == '/') && req.path().equals(root.path())"));
        try (final var loader = loader(); final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            final var root = loader.loadClass("demo.App").getConstructor().newInstance();
            assertEquals(202, route(endpoints(loader, root, mapper), "/java")
                    .handle(new TestRequest("GET", "/java", "", "stream")).toCompletableFuture().join().status());
        }
        boolean found = false;
        try (final var files = Files.walk(work.resolve("classes/demo"))) {
            for (final var path : files.filter(p -> p.toString().endsWith(".class") && (p.getFileName().toString().startsWith("FusionBindings") || p.getFileName().toString().startsWith("Module"))).toList()) {
                final var output = new StringWriter();
                assertEquals(0, java.util.spi.ToolProvider.findFirst("javap").orElseThrow().run(new PrintWriter(output), new PrintWriter(output),
                        "-v", "-p", path.toString()));
                found |= output.toString().contains("LambdaMetafactory");
            }
        }
        assertTrue(found, "The explicit user matcher lambda must be retained, not interpreted or rewritten");
    }

    @Test
    void allDeclarativeMatcherModesAndNoConfigurationCommand() throws Exception {
        compile("""
                package demo;
                import io.yupiik.fusion.framework.build.api.http.*;
                import io.yupiik.fusion.framework.build.api.cli.Command;
                import io.yupiik.fusion.http.server.api.*;
                public class App {
                    @HttpMatcher(methods={"GET"}, path="/exact", pathMatching=HttpMatcher.PathMatching.EXACT, priority=1)
                    public Response exact() { return Response.of().status(201).build(); }
                    @HttpMatcher(path="/prefix", pathMatching=HttpMatcher.PathMatching.STARTS_WITH, priority=2)
                    public Response prefix() { return Response.of().status(202).build(); }
                    @HttpMatcher(path="suffix", pathMatching=HttpMatcher.PathMatching.ENDS_WITH, priority=3)
                    public Response suffix() { return Response.of().status(203).build(); }
                    @HttpMatcher(priority=4) public Response ignored() { return Response.of().status(204).build(); }
                    @Command(name="plain", description="no configuration")
                    public static class Plain implements Runnable { public Plain() {} public void run() {} }
                }
                """, "-Afusion.generateModule=false", "-Afusion.generateBeanForCliCommands=false");
        try (final var loader = loader()) {
            final var root = loader.loadClass("demo.App").getConstructor().newInstance();
            final var endpoints = endpoints(loader, root, null);
            assertEquals(4, endpoints.size());
            for (final var endpoint : endpoints) {
                final var path = switch (endpoint.priority()) { case 1 -> "/exact"; case 2 -> "/prefix/more"; case 3 -> "/any-suffix"; default -> "/whatever"; };
                assertTrue(endpoint.matches(new TestRequest("get", path, "", "stream")));
                assertEquals(200 + endpoint.priority(), endpoint.handle(null).toCompletableFuture().join().status());
                if (endpoint.priority() < 4) {
                    assertFalse(endpoint.matches(new TestRequest("GET", null, "", "stream")));
                    assertFalse(endpoint.matches(new TestRequest("POST", "/other", "", "stream")));
                }
            }
            final var command = commands(loader).get(0).apply(null);
            assertEquals("--any-key", command.keyMapper().apply("any.key"));
            try (final var instance = command.create(key -> Optional.empty(), new ArrayList<>())) {
                instance.instance().run();
            }
        }
        assertCompactOutput();
    }

    @Test
    void rpcScalarCollectionOptionalNullAndRequestMetadata() throws Exception {
        compile("""
                package demo;
                import io.yupiik.fusion.framework.build.api.jsonrpc.*;
                import io.yupiik.fusion.http.server.api.Request;
                import java.util.*;
                public class App {
                    @JsonRpc("values")
                    public String values(int primitive, Integer boxed, Request request, List<Long> numbers,
                            Map<String, Double> values, Optional<Boolean> flag, Set<String> names) {
                        return primitive + ":" + boxed + ":" + numbers + ":" + values + ":" + flag + ":" + names;
                    }
                    @JsonRpc("meta")
                    public String meta(Request request, @JsonRpcParam(value="documented", required=true, documentation="parameter documentation") int value) {
                        return "" + value;
                    }
                }
                """, "-Afusion.generateModule=false");
        try (final var loader = loader(); final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            final var root = loader.loadClass("demo.App").getConstructor().newInstance();
            final var method = methods(loader, root, mapper).stream().filter(m -> m.name().equals("values")).findFirst().orElseThrow();
            assertEquals("0:null:null:null:Optional.empty:null", method.invoke(new JsonRpcMethod.Context(null, null)).toCompletableFuture().join());
            assertEquals("1:2:[3]:{a=4.5}:Optional[true]:[name]", method.invoke(new JsonRpcMethod.Context(null,
                    List.of(1, 2, List.of(3), Map.of("a", 4.5), true, List.of("name", "name")))).toCompletableFuture().join());
            assertEquals(-32700, assertThrows(JsonRpcException.class, () -> method.invoke(new JsonRpcMethod.Context(null,
                    Map.of("numbers", "not a collection")))).code());
        }
        try (final var files = Files.walk(work.resolve("classes"))) {
            final var metadata = files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).contains("openrpc")).toList();
            assertFalse(metadata.isEmpty(), "OpenRPC metadata must still be generated");
            assertTrue(Files.readString(metadata.get(0)).contains("parameter documentation"));
        }
        assertCompactOutput();
    }

    private DefaultEndpoint route(final List<DefaultEndpoint> endpoints, final String path) {
        return endpoints.stream().filter(e -> e.matches(new TestRequest("GET", path, "", "stream"))).findFirst().orElseThrow();
    }

    private List<DefaultEndpoint> endpoints(final ClassLoader loader, final Object root, final Object mapper) throws Exception {
        final var result = new ArrayList<DefaultEndpoint>();
        for (final var factory : this.<Object[], DefaultEndpoint>factories(loader, "$FusionHttp")) {
            result.add(factory.apply(new Object[]{root, mapper}));
        }
        return result;
    }

    private List<DefaultJsonRpcMethod> methods(final ClassLoader loader, final Object root, final Object mapper) throws Exception {
        final var result = new ArrayList<DefaultJsonRpcMethod>();
        for (final var factory : this.<Object[], DefaultJsonRpcMethod>factories(loader, "$FusionJsonRpcMethod")) {
            result.add(factory.apply(new Object[]{root, mapper}));
        }
        return result;
    }

    private List<Function<RuntimeContainer, CliCommand<?>>> commands(final ClassLoader loader) throws Exception {
        return factories(loader, "$FusionCliCommand");
    }

    @SuppressWarnings("unchecked")
    private <D, T> List<Function<D, T>> factories(final ClassLoader loader, final String suffix) {
        final var result = new ArrayList<Function<D, T>>();
        for (final var method : GeneratedFactories.methods(loader, "demo", Function.class)) {
            if (method.getName().contains(suffix)) {
                result.add((Function<D, T>) GeneratedFactories.invoke(loader, "demo", method.getName(), Function.class));
            }
        }
        return result;
    }

    private void assertCompactOutput() throws Exception {
        try (final var files = Files.walk(work.resolve("classes/demo"))) {
            for (final var path : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                final var name = path.getFileName().toString();
                assertFalse(name.contains("$FusionHttp") || name.contains("$FusionJsonRpc") || name.contains("$FusionCli"), name);
                if (name.startsWith("Fusion") || name.startsWith("Module")) {
                    assertDirectCallbacks("demo." + name.replace(".class", ""));
                }
            }
        }
        for (final var type : List.of(DirectEndpointHandler.class, BaseCliCommand.class)) {
            assertDirectCallbacks(type.getName());
        }
    }

    private void assertDirectCallbacks(final String type) {
        final var output = new StringWriter();
        assertEquals(0, java.util.spi.ToolProvider.findFirst("javap").orElseThrow().run(new PrintWriter(output), new PrintWriter(output),
                "-c", "-p", "-classpath", work.resolve("classes") + File.pathSeparator + System.getProperty("java.class.path"), type), output.toString());
        assertFalse(output.toString().lines().anyMatch(line -> line.contains("invokeinterface") && line.contains("Dispatch.invoke:")), output.toString());
        assertFalse(output.toString().contains("java/lang/reflect/Method"), output.toString());
    }

    private URLClassLoader loader() throws Exception {
        return new URLClassLoader(new URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader());
    }

    private void compile(final String source, final String... extra) throws Exception {
        final var path = work.resolve("sources/demo/App.java");
        Files.createDirectories(path.getParent());
        Files.writeString(path, source);
        Files.createDirectories(work.resolve("classes"));
        Files.createDirectories(work.resolve("generated"));
        final var compiler = ToolProvider.getSystemJavaCompiler();
        final var output = new StringWriter();
        try (final var manager = compiler.getStandardFileManager(null, null, null)) {
            final var options = new ArrayList<>(List.of("--release", "17", "-classpath",
                    work.resolve("classes") + File.pathSeparator + System.getProperty("java.class.path"),
                    "-d", work.resolve("classes").toString(), "-s", work.resolve("generated").toString(),
                    "-Afusion.workdir=" + work.resolve("state"), "-Afusion.moduleFqn=demo.Module", "-Xlint:all,-processing,-serial"));
            options.addAll(List.of(extra));
            final var task = compiler.getTask(output, manager, null, options, null, manager.getJavaFileObjects(path));
            task.setProcessors(List.of(new FusionProcessor()));
            assertTrue(task.call(), output.toString());
        }
    }

    private static String text(final Response response) {
        final var out = new StringBuilder();
        response.body().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(final Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override
            public void onNext(final java.nio.ByteBuffer buffer) { out.append(StandardCharsets.UTF_8.decode(buffer)); }
            @Override
            public void onError(final Throwable error) { throw new AssertionError(error); }
            @Override
            public void onComplete() {}
        });
        return out.toString();
    }

    private static final class TestRequest implements Request {
        private final String method;
        private final String path;
        private final String payload;
        private final String mode;
        private final Map<String, Object> attributes = new HashMap<>();

        private TestRequest(final String method, final String path, final String payload, final String mode) {
            this.method = method;
            this.path = path;
            this.payload = payload;
            this.mode = mode;
        }
        @Override
        public String scheme() { return "http"; }
        @Override
        public String method() { return method; }
        @Override
        public String path() { return path; }
        @Override
        public String query() { return null; }
        @Override
        public Body fullBody() {
            assertEquals("reactive", mode, "Stream and reader decoding must not subscribe to the publisher");
            return new TestBody(payload);
        }
        @Override
        public Stream<Cookie> cookies() { return Stream.empty(); }
        @Override
        public String parameter(final String name) { return null; }
        @Override
        public Map<String, String[]> parameters() { return Map.of(); }
        @Override
        public String header(final String name) { return null; }
        @Override
        public Map<String, List<String>> headers() { return Map.of(); }
        @Override
        public <T> T attribute(final String key, final Class<T> type) { return type.cast(attributes.get(key)); }
        @Override
        public <T> void setAttribute(final String key, final T value) { attributes.put(key, value); }
        @Override
        public <T> T unwrapOrNull(final Class<T> type) {
            if (type == InputStream.class && (mode.equals("stream") || mode.equals("both"))) {
                return type.cast(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
            }
            if (type == Reader.class && mode.equals("both")) {
                return type.cast(new StringReader("\"wrong reader\""));
            }
            if (type == Reader.class && mode.equals("reader")) {
                return type.cast(new StringReader(payload));
            }
            return null;
        }
    }

    private record TestBody(String value) implements Body {
        @Override
        public Body cached() { return this; }
        @Override
        public CompletionStage<String> string() { return CompletableFuture.completedStage(value); }
        @Override
        public CompletionStage<byte[]> bytes() { return CompletableFuture.completedStage(value.getBytes(StandardCharsets.UTF_8)); }
        @Override
        public String parameter(final String name) { return null; }
        @Override
        public void subscribe(final Flow.Subscriber<? super java.nio.ByteBuffer> subscriber) {
            new BytesPublisher(value.getBytes(StandardCharsets.UTF_8)).subscribe(subscriber);
        }
    }
}
