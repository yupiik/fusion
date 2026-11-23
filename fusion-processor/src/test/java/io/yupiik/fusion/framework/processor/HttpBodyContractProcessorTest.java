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
import io.yupiik.fusion.http.server.api.Body;
import io.yupiik.fusion.http.server.api.Cookie;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.impl.DefaultEndpoint;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.tools.ToolProvider;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpBodyContractProcessorTest {
    @TempDir
    Path work;

    @ParameterizedTest
    @ValueSource(strings = {"HttpMatcher", "HttpJavaMatcher"})
    void rejectsMultipleBodiesWithMethodDiagnostic(final String family) throws Exception {
        final var output = new StringWriter();
        assertFalse(compile(source(family, """
                public Response invalid(Request first, String body, Request middle, Integer other, Request last) {
                    throw new AssertionError("Invalid endpoint must never be invoked");
                }
                """), output), output.toString());
        assertTrue(output.toString().contains("HTTP endpoints support at most one JSON body parameter"), output.toString());
        assertTrue(output.toString().contains("demo.App.invalid("), output.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"HttpMatcher", "HttpJavaMatcher"})
    void invokesZeroOrOneBodyWithRequestParametersInAnyPosition(final String family) throws Exception {
        final var annotation = annotation(family);
        final var output = new StringWriter();
        assertTrue(compile(source(family, "public Response none() { return Response.of().status(201).build(); }\n" +
                annotation + " public Response requests(Request first, Request last) { return result(first, last, \"value\", 202); }\n" +
                annotation + " public Response only(String body) { return result(null, null, body, 203); }\n" +
                annotation + " public Response before(Request request, String body) { return result(request, request, body, 204); }\n" +
                annotation + " public Response after(String body, Request request) { return result(request, request, body, 205); }\n" +
                annotation + " public Response around(Request first, String body, Request last) { return result(first, last, body, 206); }\n" +
                "private Response result(Request first, Request last, String body, int status) {\n" +
                " if (first != last || (status != 203 && first == null) || (first != null && !\"/test\".equals(first.path())) || !\"value\".equals(body)) { throw new AssertionError(); }\n" +
                " return Response.of().status(status).build(); }"), output), output.toString());
        try (final var loader = new URLClassLoader(new URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader());
             final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            final var root = loader.loadClass("demo.App").getConstructor().newInstance();
            final var request = new TestRequest();
            final var statuses = new java.util.TreeSet<Integer>();
            for (final var method : GeneratedFactories.methods(loader, "demo", Function.class)) {
                @SuppressWarnings("unchecked")
                final var factory = (Function<Object[], DefaultEndpoint>) GeneratedFactories.invoke(loader, "demo", method.getName(), Function.class);
                final var endpoint = factory.apply(new Object[]{root, mapper});
                assertTrue(endpoint.matches(request));
                statuses.add(endpoint.handle(request).toCompletableFuture().join().status());
            }
            assertEquals(java.util.Set.of(201, 202, 203, 204, 205, 206), statuses);
            assertEquals(4, request.reads);
        }
    }

    private String annotation(final String family) {
        return "HttpMatcher".equals(family) ? "@HttpMatcher" : "@HttpJavaMatcher(\"true\")";
    }

    private String source(final String family, final String methods) {
        return "package demo;\n" +
                "import io.yupiik.fusion.framework.build.api.http.*;\n" +
                "import io.yupiik.fusion.http.server.api.*;\n" +
                "public class App {\n" + annotation(family) + "\n" + methods + "\n}";
    }

    private boolean compile(final String source, final StringWriter output) throws Exception {
        final var path = work.resolve("sources/demo/App.java");
        Files.createDirectories(path.getParent());
        Files.writeString(path, source);
        Files.createDirectories(work.resolve("classes"));
        Files.createDirectories(work.resolve("generated"));
        final var compiler = ToolProvider.getSystemJavaCompiler();
        try (final var manager = compiler.getStandardFileManager(null, null, null)) {
            final var task = compiler.getTask(output, manager, null, List.of("--release", "17", "-classpath",
                    System.getProperty("java.class.path"), "-d", work.resolve("classes").toString(),
                    "-s", work.resolve("generated").toString(), "-Afusion.workdir=false",
                    "-Afusion.generateModule=false", "-Afusion.generateBeanForHttpEndpoints=false"),
                    null, manager.getJavaFileObjects(path));
            task.setProcessors(List.of(new FusionProcessor()));
            return task.call();
        }
    }

    private static final class TestRequest implements Request {
        private int reads;
        @Override
        public <T> T unwrapOrNull(final Class<T> type) {
            if (type == java.io.Reader.class) {
                reads++;
                return type.cast(new StringReader("\"value\""));
            }
            return null;
        }
        @Override
        public Body fullBody() { throw new AssertionError("Reader must take precedence"); }
        @Override
        public String scheme() { return "http"; }
        @Override
        public String method() { return "GET"; }
        @Override
        public String path() { return "/test"; }
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
        public <T> void setAttribute(final String key, final T value) { }
    }
}
