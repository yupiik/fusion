/*
 * Copyright (c) 2022-2023 - Yupiik SAS - https://www.yupiik.com
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
package io.yupiik.fusion.framework.processor.test;

import io.yupiik.fusion.cli.internal.CliCommand;
import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.container.Generation;
import io.yupiik.fusion.framework.build.api.scanning.Injection;
import io.yupiik.fusion.framework.processor.Bean;
import io.yupiik.fusion.framework.processor.FusionProcessor;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.jsonrpc.JsonRpcHandler;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class Compiler {
    private final Path work;
    private final String[] classNames;
    private Path src;
    private Path generatedSources;
    private Path classes;

    public Compiler(final Path work, final String... classNames) {
        this.work = work;
        this.classNames = classNames;
    }

    public Path getGeneratedSources() {
        return generatedSources;
    }

    public Path getSrc() {
        return src;
    }

    public Path getClasses() {
        return classes;
    }

    @SafeVarargs
    public final void jsonRoundTripAsserts(final String clazz, final String json, final String toString,
                                           final BiConsumer<Function<String, Class<?>>, JsonMapper>... customChecks) throws IOException {
        compileAndJsonAsserts((loader, mapper) -> {
            final var model = loader.apply(clazz);
            final Object instance;
            try (final var reader = new StringReader(json)) {
                instance = mapper.read(model, reader);
                assertNotNull(instance);
            }

            assertEquals(toString, instance.toString());

            final var writer = new StringWriter();
            try (writer) {
                mapper.write(instance, writer);
            } catch (final IOException e) {
                fail(e);
            }
            assertEquals(json, writer.toString());

            Stream.of(customChecks).forEach(c -> c.accept(loader, mapper));
        });
    }

    public void compileAndJsonAsserts(final BiConsumer<Function<String, Class<?>>, JsonMapper> test) throws IOException {
        compileAndAsserts((loader, container) -> {
            try (final var instance = container.lookup(JsonMapper.class)) {
                test.accept(loader, instance.instance());
            }
        });
    }

    public void compileAndAsserts(final Consumer<Instance<?>> test) throws IOException {
        compileAndAssertsInstance((container, instance) -> test.accept(instance));
    }

    public void compileAndAssertsInstance(final BiConsumer<RuntimeContainer, Instance<?>> test) throws IOException {
        compileAndAsserts((loader, container) -> {
            try (final var instance = container.lookup(loader.apply("test.p." + classNames[0]))) {
                test.accept(container, instance);
            }
        });
    }

    public void compileAndAsserts(final BiConsumer<Function<String, Class<?>>, RuntimeContainer> test,
                                  final FusionBean<?>... beans) throws IOException {
        final var classes = assertCompiles(setupSrc());
        try (final var loader = new CompilationClassLoader(classes);
             final var container = ConfiguringContainer.of().register(beans).start()) {
            test.accept(s -> {
                try {
                    return loader.loadClass(s);
                } catch (final ClassNotFoundException e) {
                    return fail(e);
                }
            }, container);
        }
    }

    public Compiler assertCompiles(final int exitCode) {
        try {
            assertCompiles(setupSrc(), exitCode);
        } catch (final IOException e) {
            fail(e);
        }
        return this;
    }

    private Path assertCompiles(final Path src) {
        return assertCompiles(src, 0);
    }

    private Path assertCompiles(final Path src, final int exitCode) {
        this.src = src;
        this.classes = work.resolve("classes");
        this.generatedSources = src.getParent().resolve("generated-sources");

        final var version = Runtime.version().version().get(0).toString();
        final var cp = String.join(
                File.pathSeparator,
                Bean.class.getProtectionDomain().getCodeSource().getLocation().getFile(),
                Injection.class.getProtectionDomain().getCodeSource().getLocation().getFile(),
                Generation.class.getProtectionDomain().getCodeSource().getLocation().getFile(),
                JsonMapper.class.getProtectionDomain().getCodeSource().getLocation().getFile(),
                Request.class.getProtectionDomain().getCodeSource().getLocation().getFile(),
                JsonRpcHandler.class.getProtectionDomain().getCodeSource().getLocation().getFile(),
                CliCommand.class.getProtectionDomain().getCodeSource().getLocation().getFile());
        final var cmd = Stream.concat(
                        Stream.of(
                                "--release", version,
                                "--source-path", src.toString(),
                                "--class-path", cp,
                                "-s", generatedSources.toString(),
                                "-d", classes.toString(),
                                "-parameters",
                                "-implicit:class",
                                "-Werror",
                                "-Xlint:unchecked",
                                "-Afusion.skipNotes=false",
                                // "-verbose",
                                "-processor", FusionProcessor.class.getName()),
                        Stream.of(classNames).map(it -> "test.p." + it))
                .toArray(String[]::new);
        final var compiled = ToolProvider
                .findFirst("javac")
                .orElseThrow()
                .run(System.out, System.err, cmd);
        assertEquals(exitCode, compiled, () -> "Compiled Status=" + compiled + "\n" + list(generatedSources));
        return classes;
    }

    public Path setupSrc() throws IOException {
        final var src = work.resolve("src");
        final var pck = Files.createDirectories(src.resolve("test/p"));
        final var tccl = Thread.currentThread().getContextClassLoader();
        Stream.of(classNames).forEach(clazz -> {
            try (final var in = tccl.getResourceAsStream("test/p/" + clazz + ".java")) {
                Files.copy(
                        requireNonNull(in, () -> "Missing '" + clazz + ".java'"), pck.resolve(clazz + ".java"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (final IOException e) {
                fail(e);
            }
        });
        return src;
    }

    private String list(final Path src) {
        if (!Files.exists(src)) {
            return "";
        }
        try (final var list = Files.walk(src)) {
            return list
                    .filter(Files::isRegularFile)
                    .map(p -> {
                        try {
                            return p.getFileName() + ":\n" + Files.readString(p);
                        } catch (final IOException e) {
                            return p.getFileName().toString();
                        }
                    })
                    .collect(joining("\n-------\n"));
        } catch (final IOException e) {
            return fail(e);
        }
    }
}
