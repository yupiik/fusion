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
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.http.server.api.Body;
import io.yupiik.fusion.http.server.api.Cookie;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.spi.Endpoint;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.tools.ToolProvider;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpEndpointOrderingProcessorTest {
    @TempDir
    Path work;

    @ParameterizedTest
    @ValueSource(strings = {"HttpMatcher", "HttpJavaMatcher"})
    void moduleLocalLexicalOrderAndSuppliedCrossModuleTies(final String family) throws Exception {
        final var a = compile("a", family);
        final var z = compile("z", family);
        try (final var loader = new URLClassLoader(new URL[]{a.toUri().toURL(), z.toUri().toURL()}, getClass().getClassLoader())) {
            for (final var order : List.of(List.of("z", "a"), List.of("a", "z"))) {
                final var configuring = ConfiguringContainer.of().disableAutoDiscovery(true);
                for (final var module : order) {
                    configuring.register((FusionModule) loader.loadClass("ordering." + module + ".Module").getConstructor().newInstance());
                }
                try (final var container = configuring.start();
                     final var endpoints = container.lookups(Endpoint.class, instances -> instances.stream().map(Instance::instance).toList())) {
                    final var supplied = endpoints.instance();
                    final var first = order.get(0);
                    final var second = order.get(1);
                    // All equal-priority generated endpoints share their runtime adapter type.
                    // Their lexical order is local to each module, not globally sorted by former generated class name.
                    assertEquals(List.of(first + ":alphaBroad", first + ":zuluExact", second + ":alphaBroad", second + ":zuluExact"),
                            supplied.stream().filter(endpoint -> endpoint.priority() == 1000)
                                    .map(endpoint -> label(endpoint, "/shared")).toList());
                    assertEquals(first + ":alphaBroad", dispatch(supplied, "/shared"));
                    assertEquals(first + ":betaHigh", dispatch(supplied, "/override"));
                    assertEquals(first + ":all", dispatch(supplied, "/fallback"));

                    final var beanPriorities = container.getBeans().getBeans().values().stream().flatMap(List::stream)
                            .filter(bean -> bean.type().getTypeName().equals("io.yupiik.fusion.http.server.impl.DefaultEndpoint"))
                            .map(bean -> bean.priority()).sorted().toList();
                    assertEquals(List.of(17, 17, 1000, 1000, 1000, 1000, 1000, 1000), beanPriorities,
                            "@Order is bean ordering, not routing priority");

                    final var manual = Endpoint.of(request -> true, request -> completedFuture(
                            Response.of().header("selected", "manual").build()), 1000);
                    final var manualFirst = new ArrayList<Endpoint>();
                    manualFirst.add(manual);
                    manualFirst.addAll(supplied);
                    assertEquals("manual", dispatch(manualFirst, "/shared"));
                    final var generatedFirst = new ArrayList<>(supplied);
                    generatedFirst.add(manual);
                    assertEquals(first + ":alphaBroad", dispatch(generatedFirst, "/shared"));
                    // Even a manually supplied catch-all must yield to a higher generated routing priority.
                    assertEquals(first + ":betaHigh", dispatch(manualFirst, "/override"));
                }
            }
        }
    }

    private String dispatch(final List<Endpoint> supplied, final String path) {
        final var request = new TestRequest(path);
        final var selected = supplied.stream().sorted(Comparator.comparingInt(Endpoint::priority).reversed())
                .filter(endpoint -> endpoint.matches(request)).findFirst().orElseThrow();
        return label(selected, path);
    }

    private String label(final Endpoint endpoint, final String path) {
        return endpoint.handle(new TestRequest(path)).toCompletableFuture().join().headers().get("selected").get(0);
    }

    private Path compile(final String module, final String family) throws Exception {
        final var root = Files.createDirectories(work.resolve(module));
        final var source = root.resolve("sources/ordering/" + module + "/Routes.java");
        Files.createDirectories(source.getParent());
        final var broad = "HttpMatcher".equals(family) ?
                "@HttpMatcher(path=\"/shared\", pathMatching=HttpMatcher.PathMatching.STARTS_WITH)" :
                "@HttpJavaMatcher(\"req.path().startsWith(\\\"/shared\\\")\")";
        final var exact = "HttpMatcher".equals(family) ?
                "@HttpMatcher(path=\"/shared\", pathMatching=HttpMatcher.PathMatching.EXACT)" :
                "@HttpJavaMatcher(\"req.path().equals(\\\"/shared\\\")\")";
        final var high = "HttpMatcher".equals(family) ?
                "@HttpMatcher(priority=2000, path=\"/override\", pathMatching=HttpMatcher.PathMatching.EXACT)" :
                "@HttpJavaMatcher(priority=2000, value=\"req.path().equals(\\\"/override\\\")\")";
        final var all = "HttpMatcher".equals(family) ? "@HttpMatcher(priority=0)" : "@HttpJavaMatcher(priority=0)";
        Files.writeString(source, """
                package ordering.%s;
                import io.yupiik.fusion.framework.build.api.http.*;
                import io.yupiik.fusion.framework.build.api.order.Order;
                import io.yupiik.fusion.http.server.api.Response;
                public class Routes {
                    %s public Response zuluExact() { return selected("zuluExact"); }
                    %s public Response alphaBroad() { return selected("alphaBroad"); }
                    %s @Order(17) public Response betaHigh() { return selected("betaHigh"); }
                    %s public Response all() { return selected("all"); }
                    private Response selected(String name) { return Response.of().header("selected", "%s:" + name).build(); }
                }
                """.formatted(module, exact, broad, high, all, module));
        final var classes = Files.createDirectories(root.resolve("classes"));
        final var generated = Files.createDirectories(root.resolve("generated"));
        final var compiler = ToolProvider.getSystemJavaCompiler();
        final var diagnostics = new StringWriter();
        try (final var manager = compiler.getStandardFileManager(null, null, null)) {
            final var task = compiler.getTask(diagnostics, manager, null, List.of("--release", "17", "-classpath",
                    System.getProperty("java.class.path"), "-d", classes.toString(), "-s", generated.toString(),
                    "-Afusion.workdir=false", "-Afusion.moduleFqn=ordering." + module + ".Module",
                    "-Afusion.skipLoadingModules=true", "-Afusion.generateNativeImage=false"), null, manager.getJavaFileObjects(source));
            task.setProcessors(List.of(new FusionProcessor()));
            assertTrue(task.call(), diagnostics.toString());
        }
        return classes;
    }

    private record TestRequest(String path) implements Request {
        @Override
        public Body fullBody() { throw new AssertionError("No request body expected"); }
        @Override
        public String scheme() { return "http"; }
        @Override
        public String method() { return "GET"; }
        @Override
        public String query() { return null; }
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
        public <T> T attribute(final String key, final Class<T> type) { return null; }
        @Override
        public <T> void setAttribute(final String key, final T value) { throw new AssertionError("No matcher attributes expected"); }
    }
}
