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
package io.yupiik.fusion.httpclient.core.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test of the multi-release Java 21 variant of {@link HttpClientCloser}.
 * <p>
 * The JVM only picks META-INF/versions/21 classes from a jar (not from an exploded directory),
 * so this test builds a minimal multi-release jar from the test build output and runs a probe
 * in a separate process with that jar on the classpath. The probe does a direct (non reflective)
 * call to {@code HttpClientCloser.close(client, timeout)} which the runtime resolves to the
 * Java 21 variant, and exercises the shutdown/awaitTermination/shutdownNow sequence.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpClientCloserJava21Test {

    @Test
    void java21VariantClosesWithShutdownNow() throws Exception {
        assumeTrue(Runtime.version().feature() >= 21, "requires Java 21+");
        assumeTrue(isMultiReleaseCompiled(), "the jdk21-multirelease profile must have compiled the variant");

        final var probe = buildProbeJar();
        final var result = runProbe(probe.jar());
        assertTrue(result.contains("close-fast=true"), "the close must return quickly, got: " + result);
        assertTrue(result.contains("terminated=true"), "the client must be terminated, got: " + result);
        assertTrue(result.contains("no-timeout-terminated=false"), "null timeout must not terminate, got: " + result);
    }

    private static boolean isMultiReleaseCompiled() {
        return Files.exists(mainClassesDir().resolve("META-INF").resolve("versions").resolve("21")
                .resolve("io").resolve("yupiik").resolve("fusion").resolve("httpclient").resolve("core")
                .resolve("internal").resolve("HttpClientCloser.class"));
    }

    private static Path mainClassesDir() {
        final var testClasses = classesDir();
        final var target = testClasses.getParent();
        return target.resolve("classes"); // target/classes
    }

    private static Path classesDir() {
        try {
            final var url = HttpClientCloserJava21Test.class.getResource("HttpClientCloserJava21Test.class");
            final var path = Paths.get(url.toURI());
            // .../test-classes/io/yupiik/fusion/httpclient/core/internal/HttpClientCloserJava21Test.class
            var dir = path.getParent();
            for (int i = 0; i < 6; i++) { // internal -> core -> httpclient -> fusion -> yupiik -> io -> test-classes
                dir = dir.getParent();
            }
            return dir; // target/test-classes
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private record Probe(Path jar) {
    }

    private static Probe buildProbeJar() throws Exception {
        final var work = Files.createTempDirectory("fusion-httpclient-java21-");
        final var jar = work.resolve("probe.jar");

        // copy the main classes into the jar, including META-INF/versions/21
        try (final var out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            out.write(("Manifest-Version: 1.0\nMulti-Release: true\n\n").getBytes());
            out.closeEntry();

            final var mainClasses = mainClassesDir(); // target/classes
            if (!Files.exists(mainClasses)) {
                throw new IllegalStateException("target/classes not found: " + mainClasses);
            }
            // same package files
            final var packageDir = "io/yupiik/fusion/httpclient/core/internal";
            copyIntoJar(out, mainClasses.resolve(packageDir), packageDir, "HttpClientCloser.class");
            // versioned variant
            final var versions21 = mainClasses.resolve("META-INF").resolve("versions").resolve("21").resolve(packageDir);
            copyIntoJar(out, versions21, "META-INF/versions/21/" + packageDir, "HttpClientCloser.class");
            // probe main (from the test classpath)
            copyIntoJar(out, classesDir().resolve(packageDir), packageDir, "ProbeMain.class");
        }
        return new Probe(jar);
    }

    private static void copyIntoJar(final JarOutputStream out, final Path dir, final String entryPrefix, final String name) throws IOException {
        final var file = dir.resolve(name);
        assertTrue(Files.exists(file), "missing: " + file);
        try (final var in = Files.newInputStream(file)) {
            out.putNextEntry(new JarEntry(entryPrefix + "/" + name));
            in.transferTo(out);
            out.closeEntry();
        }
    }

    private static String runProbe(final Path jar) throws Exception {
        final var java = Paths.get(System.getProperty("java.home"), "bin", "java");
        final var process = new ProcessBuilder(
                java.toString(),
                "-cp", jar.toString(),
                "io.yupiik.fusion.httpclient.core.internal.ProbeMain")
                .redirectErrorStream(true)
                .start();
        final var output = new String(process.getInputStream().readAllBytes());
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("probe timed out, output: " + output);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("probe exited with " + process.exitValue() + ", output: " + output);
        }
        return output;
    }
}