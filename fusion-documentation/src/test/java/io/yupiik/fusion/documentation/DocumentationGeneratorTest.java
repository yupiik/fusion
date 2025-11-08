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
package io.yupiik.fusion.documentation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentationGeneratorTest {
    private static final String DOC_CONTENT = """
            {
               "version":1,
               "classes":{
                  "io.yupiik.test.JwtValidatorConfiguration":[
                     {
                        "name":"jwt.algo",
                        "documentation":"JWT `alg` value.",
                        "defaultValue":"\\"RS256\\"",
                        "required":false
                     },
                     {
                        "name":"jwt.expRequired",
                        "documentation":"Are `exp` (expiry) validation required of can it be skipped if claim is missing.",
                        "defaultValue":true,
                        "required":false
                     }
                  ]
               },
               "roots":[
                  "io.yupiik.test.JwtValidatorConfiguration"
               ]
            }""";

    @Test
    void genericMavenModuleNameLookup(@TempDir final Path work) throws IOException {
        final var localRepo = Files.createDirectories(work.resolve("target/project-local-repo/com.foo-with-iphen/artifact/0.0.1-SNAPSHOT"));
        final var jarFile = localRepo.resolve("artifact-0.0.1-SNAPSHOT.jar");
        try (final var jar = new JarOutputStream(Files.newOutputStream(jarFile))) {
            jar.putNextEntry(new JarEntry("META-INF/"));
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("META-INF/fusion/"));
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("META-INF/fusion/configuration/"));
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("META-INF/fusion/configuration/documentation.json"));
            jar.write(DOC_CONTENT.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }

        final var base = Files.createDirectories(work.resolve("base"));
        new DocumentationGenerator(base, Map.of(
                "urls", "jar:" + jarFile.toUri().toURL().toExternalForm() + "!/META-INF/fusion/configuration/documentation.json",
                "includeEnvironmentNames", "true"))
                .run();
        assertEquals("""
                = artifact
                
                == Configuration
                
                * `jwt.algo` (`JWT_ALGO`) (default: `"RS256"`): JWT `alg` value.
                * `jwt.expRequired` (`JWT_EXPREQUIRED`) (default: `true`): Are `exp` (expiry) validation required of can it be skipped if claim is missing.""",
                Files.readString(base.resolve("content/_partials/generated/documentation.artifact.adoc")));
    }

    @Test
    void defaultFormatting(@TempDir final Path work) throws IOException {
        final var url = writeConf(work);
        final var output = work.resolve("output.adoc");
        new DocumentationGenerator(Files.createDirectories(work.resolve("base")), Map.of(
                "module", "test-module",
                "urls", url.toExternalForm(),
                "includeEnvironmentNames", "true",
                "output", output.toString()))
                .run();
        assertEquals("""
                = test-module
                
                == Configuration
                
                * `jwt.algo` (`JWT_ALGO`) (default: `"RS256"`): JWT `alg` value.
                * `jwt.expRequired` (`JWT_EXPREQUIRED`) (default: `true`): Are `exp` (expiry) validation required of can it be skipped if claim is missing.""", Files.readString(output));
    }

    @Test
    void tableFormatting(@TempDir final Path work) throws IOException {
        final var url = writeConf(work);
        final var output = work.resolve("output.adoc");
        new DocumentationGenerator(Files.createDirectories(work.resolve("base")), Map.of(
                "formatter", "table",
                "module", "test-module",
                "urls", url.toExternalForm(),
                "includeEnvironmentNames", "true",
                "output", output.toString()))
                .run();
        assertEquals("""
                [options="header",cols="a,a,2a"]
                |===
                |Name |Env Variable |Description |Default
                
                | `jwt.algo`\s
                | `JWT_ALGO`
                | JWT `alg` value.
                | `RS256`
                
                | `jwt.expRequired`\s
                | `JWT_EXPREQUIRED`
                | Are `exp` (expiry) validation required of can it be skipped if claim is missing.
                | `true`
                |===
                """, Files.readString(output));
    }

    @Test
    void definitionListFormatting(@TempDir final Path work) throws IOException {
        final var url = writeConf(work);
        final var output = work.resolve("output.adoc");
        new DocumentationGenerator(Files.createDirectories(work.resolve("base")), Map.of(
                "formatter", "definitionList",
                "module", "test-module",
                "urls", url.toExternalForm(),
                "includeEnvironmentNames", "true",
                "output", output.toString()))
                .run();
        assertEquals("""
                `jwt.algo` (env: `JWT_ALGO`)::
                JWT `alg` value. Default: `RS256`.
                `jwt.expRequired` (env: `JWT_EXPREQUIRED`)::
                Are `exp` (expiry) validation required of can it be skipped if claim is missing. Default: `true`.
                """, Files.readString(output));
    }

    private URL writeConf(final Path work) throws IOException {
        return Files.writeString(work.resolve("doc.json"), DOC_CONTENT).toUri().toURL();
    }
}
