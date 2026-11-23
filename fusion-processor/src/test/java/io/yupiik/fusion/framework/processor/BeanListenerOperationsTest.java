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
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.container.bean.FunctionalBean;
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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanListenerOperationsTest {
    private static final Pattern EMPTY_HELPER = Pattern.compile("private static void \\w+\\([^{}]*\\)\\s*\\{\\s*}");
    @TempDir
    Path work;

    private static final String SOURCE = """
            package demo;
            import io.yupiik.fusion.framework.api.scope.*;
            import io.yupiik.fusion.framework.build.api.scanning.Bean;
            import io.yupiik.fusion.framework.build.api.lifecycle.*;
            import io.yupiik.fusion.framework.build.api.event.OnEvent;
            import io.yupiik.fusion.framework.build.api.metadata.BeanMetadata;
            import io.yupiik.fusion.framework.build.api.json.JsonModel;
            import java.util.*;
            @DefaultScoped
            public class App {
                public static final List<String> trace = new ArrayList<>();
                public static boolean failClose;
                @DefaultScoped public static class Plain {}
                @DefaultScoped @BeanMetadata(name="marker", value="value")
                public static class Injected {
                    @io.yupiik.fusion.framework.build.api.scanning.Injection First first;
                    public Injected(Second second) { trace.add("constructor"); }
                    @Init public void init() { trace.add(first == null ? "missing" : "init"); }
                    @Destroy(order=7) public void stop() { trace.add("destroy"); }
                }
                @DefaultScoped public static class First {
                    public First() { trace.add("first+"); }
                    @Destroy public void stop() { trace.add("first-"); if (failClose) { throw new IllegalStateException("first-close"); } }
                }
                @DefaultScoped public static class Second {
                    public Second() { trace.add("second+"); }
                    @Destroy public void stop() { trace.add("second-"); if (failClose) { throw new IllegalStateException("second-close"); } }
                }
                @Bean public static String text() { return "text"; }
                public static class Product implements AutoCloseable {
                    public void close() { trace.add("product-"); }
                }
                @Bean @Destroy(order=12) public static Product product(First first) { return new Product(); }
                @JsonModel public record Model(String value) {}
                @DefaultScoped public static class Observer {
                    public Observer() { trace.add("root+"); }
                    @Destroy public void stop() { trace.add("root-"); if (failClose) { throw new IllegalStateException("root-close"); } }
                    public void on(@OnEvent String event, First first, Second second) {
                        trace.add(event);
                        if (event.equals("body")) { throw new IllegalArgumentException("body"); }
                    }
                    public void partial(@OnEvent Integer event, First first, Missing missing) {}
                }
                public static class Missing {}
                @Bean public static Missing missing() { throw new IllegalArgumentException("acquire"); }
            }
            """;

    @Test
    @SuppressWarnings("unchecked")
    void absentOperationsCompanionsAndSingleInjectionBody() throws Exception {
        compile();
        try (final var loader = loader()) {
            final var module = (FusionModule) loader.loadClass("demo.Module").getConstructor().newInstance();
            final var beans = module.beans().toList();
            final var trace = (List<String>) loader.loadClass("demo.App").getField("trace").get(null);
            final var injected = (FusionBean<Object>) bean(beans, "demo.App$Injected");
            assertEquals(7, injected.destroyPriority());
            assertEquals("value", injected.data().get("marker"));
            assertInstanceOf(FunctionalBean.class, injected);
            try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true).register(module).start()) {
                try (final var ignored = container.lookup(loader.loadClass("demo.App$Injected"))) {
                    assertEquals(List.of("second+", "constructor", "first+", "init"), trace);
                }
                assertEquals(List.of("second+", "constructor", "first+", "init", "destroy", "first-", "second-"), trace);
                trace.clear();
                final var dependents = new ArrayList<Instance<?>>();
                final var instance = injected.create(container, dependents);
                trace.clear();
                injected.inject(container, dependents, instance);
                assertEquals(List.of("first+"), trace, "Explicit inject must not rerun init");
                injected.destroy(container, instance);
                for (int i = dependents.size() - 1; i >= 0; i--) { dependents.get(i).close(); }
                trace.clear();
                try (final var ignored = container.lookup(loader.loadClass("demo.App$Plain"));
                     final var text = container.lookup(String.class)) {
                    assertEquals("text", text.instance());
                }
                assertTrue(trace.isEmpty());
                try (final var ignored = container.lookup(loader.loadClass("demo.App$Product"))) {
                    assertEquals(List.of("first+"), trace);
                }
                assertEquals(List.of("first+", "product-", "first-"), trace);
                final var codecBean = beans.stream().filter(b -> b.type().getTypeName().equals(
                        "io.yupiik.fusion.json.serialization.JsonCodec<demo.App$Model>")).findFirst().orElseThrow();
                final var dependencies = new ArrayList<Instance<?>>();
                final var codec = ((FusionBean<Object>) codecBean).create(container, dependencies);
                ((FusionBean<Object>) codecBean).inject(container, dependencies, codec);
                ((FusionBean<Object>) codecBean).destroy(container, codec);
                assertTrue(dependencies.isEmpty());
            }
            final var source = generated();
            assertFalse(EMPTY_HELPER.matcher(source).find(), source);
            assertEquals(1, source.split("instance.first =", -1).length - 1, source);
            assertFalse(source.contains("instance.close()"), "Producer close is shared by the runtime");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void listenerResourceOrderAndPartialAcquisitionRemainTryWithResources() throws Exception {
        compile();
        try (final var loader = loader()) {
            final var module = (FusionModule) loader.loadClass("demo.Module").getConstructor().newInstance();
            final var trace = (List<String>) loader.loadClass("demo.App").getField("trace").get(null);
            try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true).register(module).start()) {
                final var listener = (FusionListener<String>) module.listeners().filter(l -> l.eventType() == String.class).findFirst().orElseThrow();
                listener.onEvent(container, "ok");
                assertEquals(List.of("root+", "first+", "second+", "ok", "second-", "first-", "root-"), trace);
                trace.clear();
                assertEquals("body", assertThrows(IllegalArgumentException.class, () -> listener.onEvent(container, "body")).getMessage());
                assertEquals(List.of("root+", "first+", "second+", "body", "second-", "first-", "root-"), trace);
                trace.clear();
                final var partial = (FusionListener<Integer>) module.listeners().filter(l -> l.eventType() == Integer.class).findFirst().orElseThrow();
                assertEquals("acquire", assertThrows(IllegalArgumentException.class, () -> partial.onEvent(container, 1)).getMessage());
                assertEquals(List.of("root+", "first+", "first-", "root-"), trace);
                trace.clear();
                loader.loadClass("demo.App").getField("failClose").setBoolean(null, true);
                final var bodyFailure = assertThrows(IllegalArgumentException.class, () -> listener.onEvent(container, "body"));
                assertEquals("body", bodyFailure.getMessage());
                assertEquals(List.of("second-close", "first-close", "root-close"),
                        Arrays.stream(bodyFailure.getSuppressed()).map(Throwable::getMessage).toList());
                assertEquals(List.of("root+", "first+", "second+", "body", "second-", "first-", "root-"), trace);
                trace.clear();
                final var acquisitionFailure = assertThrows(IllegalArgumentException.class, () -> partial.onEvent(container, 1));
                assertEquals("acquire", acquisitionFailure.getMessage());
                assertEquals(List.of("first-close", "root-close"),
                        Arrays.stream(acquisitionFailure.getSuppressed()).map(Throwable::getMessage).toList());
                assertEquals(List.of("root+", "first+", "first-", "root-"), trace);
            }
        }
    }

    private FusionBean<?> bean(final List<FusionBean<?>> beans, final String type) {
        return beans.stream().filter(b -> b.type().getTypeName().equals(type)).findFirst().orElseThrow();
    }

    private URLClassLoader loader() throws Exception {
        return new URLClassLoader(new URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader());
    }

    private String generated() throws Exception {
        final var result = new StringBuilder();
        try (final var files = Files.walk(work.resolve("generated"))) {
            for (final var file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                result.append(Files.readString(file));
            }
        }
        return result.toString();
    }

    private void compile() throws Exception {
        final var path = work.resolve("sources/demo/App.java");
        Files.createDirectories(path.getParent());
        Files.writeString(path, SOURCE);
        Files.createDirectories(work.resolve("classes"));
        Files.createDirectories(work.resolve("generated"));
        final var compiler = ToolProvider.getSystemJavaCompiler();
        final var output = new StringWriter();
        try (final var manager = compiler.getStandardFileManager(null, null, null)) {
            final var options = List.of("--release", "17", "-classpath",
                    work.resolve("classes") + File.pathSeparator + System.getProperty("java.class.path"),
                    "-d", work.resolve("classes").toString(), "-s", work.resolve("generated").toString(),
                    "-Afusion.workdir=" + work.resolve("state"), "-Afusion.moduleFqn=demo.Module");
            final var task = compiler.getTask(output, manager, null, options, null, manager.getJavaFileObjects(path));
            task.setProcessors(List.of(new FusionProcessor()));
            assertTrue(task.call(), output.toString());
        }
    }
}
