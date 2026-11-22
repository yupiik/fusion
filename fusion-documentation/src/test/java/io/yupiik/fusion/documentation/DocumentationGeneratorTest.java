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
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void filterInclude(@TempDir final Path work) throws IOException {
        final var url = writeConf(work);
        final var output = work.resolve("output.adoc");
        new DocumentationGenerator(Files.createDirectories(work.resolve("base")), Map.of(
                "namePrefix", "jwt.a",
                "module", "test-module",
                "urls", url.toExternalForm(),
                "includeEnvironmentNames", "true",
                "output", output.toString()))
                .run();
        assertEquals("""
                = test-module
                
                == Configuration
                
                * `jwt.algo` (`JWT_ALGO`) (default: `"RS256"`): JWT `alg` value.""", Files.readString(output));
    }

    @Test
    void filterExclude(@TempDir final Path work) throws IOException {
        final var url = writeConf(work);
        final var output = work.resolve("output.adoc");
        new DocumentationGenerator(Files.createDirectories(work.resolve("base")), Map.of(
                "namePrefix", "!jwt.e",
                "module", "test-module",
                "urls", url.toExternalForm(),
                "includeEnvironmentNames", "true",
                "output", output.toString()))
                .run();
        assertEquals("""
                = test-module
                
                == Configuration
                
                * `jwt.algo` (`JWT_ALGO`) (default: `"RS256"`): JWT `alg` value.""", Files.readString(output));
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
                [options="header",cols="a,a,2a,a"]
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
    void tableFormattingWithoutEnvironmentNames(@TempDir final Path work) throws IOException {
        final var url = writeConf(work);
        final var output = work.resolve("output.adoc");
        new DocumentationGenerator(Files.createDirectories(work.resolve("base")), Map.of(
                "formatter", "table",
                "module", "test-module",
                "urls", url.toExternalForm(),
                "output", output.toString()))
                .run();
        assertEquals("""
                [options="header",cols="a,2a,a"]
                |===
                |Name |Description |Default

                | `jwt.algo`\s
                | JWT `alg` value.
                | `RS256`

                | `jwt.expRequired`\s
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

    @Test
    void selectRootsWithoutExcludingNestedClasses(@TempDir final Path work) throws IOException {
        final var metadata = Files.writeString(work.resolve("roots.json"), """
                {"classes":{
                  "Included":[{"name":"app.nested","ref":"Nested"}],
                  "Nested":[{"name":"value","documentation":"Nested value","defaultValue":0}],
                  "Ignored":[{"name":"cli.option","documentation":"CLI option"}]
                },"roots":["Included","Ignored"]}
                """);
        final var output = work.resolve("output.adoc");
        new DocumentationGenerator(work, Map.of(
                "urls", metadata.toString(), "module", "test", "roots", " Included, Included, ",
                "formatter", "definitionlist", "output", output.toString())).run();
        assertEquals("""
                `app.nested.value`::
                Nested value. Default: `0`.
                """, Files.readString(output));
    }

    @Test
    void rejectUnknownRoot(@TempDir final Path work) throws IOException {
        final var input = writeConf(work);
        final var error = assertThrows(IllegalStateException.class,
                () -> new DocumentationGenerator(work, Map.of(
                        "urls", input.toExternalForm(), "module", "test", "roots", "Missing")).run());
        assertEquals("Missing configuration root 'Missing'", error.getMessage());
    }

    @Test
    void combineRootsInsteadOfOverwritingSharedOutput(@TempDir final Path work) throws IOException {
        final var first = writeConf(work);
        final var second = Files.writeString(work.resolve("second.json"), """
                {"classes":{"Other":[{"name":"server.port","documentation":"Port","defaultValue":8080}]},"roots":["Other"]}
                """);
        final var output = work.resolve("output.adoc");
        new DocumentationGenerator(work, Map.of(
                "urls", first.toExternalForm() + ',' + second + ',' + first.toExternalForm(),
                "module", "test", "formatter", "definitionlist", "output", output.toString())).run();
        assertEquals("""
                `jwt.algo`::
                JWT `alg` value. Default: `RS256`.
                `jwt.expRequired`::
                Are `exp` (expiry) validation required of can it be skipped if claim is missing. Default: `true`.
                `server.port`::
                Port. Default: `8080`.
                """, Files.readString(output));
    }

    @Test
    void resolveReferencesAcrossInputs(@TempDir final Path work) throws IOException {
        final var first = Files.writeString(work.resolve("first.json"), """
                {"classes":{"Root":[{"name":"app.nested","ref":"Nested"}]},"roots":["Root"]}
                """);
        final var second = Files.writeString(work.resolve("second.json"), """
                {"classes":{"Nested":[{"name":"value","documentation":"Nested value","defaultValue":0}]},"roots":[]}
                """);
        final var output = work.resolve("output.adoc");
        new DocumentationGenerator(work, Map.of(
                "urls", first + "," + second, "module", "test", "roots", "Root",
                "formatter", "definitionlist", "output", output.toString())).run();
        assertEquals("""
                `app.nested.value`::
                Nested value. Default: `0`.
                """, Files.readString(output));
    }

    @Test
    void rejectConflictingClassDefinitions(@TempDir final Path work) throws IOException {
        final var first = writeConf(work);
        final var second = Files.writeString(work.resolve("second.json"), DOC_CONTENT.replace("RS256", "ES256"));
        final var error = assertThrows(IllegalStateException.class,
                () -> new DocumentationGenerator(work, Map.of(
                        "urls", first.toExternalForm() + ',' + second,
                        "module", "test", "output", work.resolve("output.adoc").toString())).run());
        assertEquals("Conflicting configuration metadata for 'io.yupiik.test.JwtValidatorConfiguration'", error.getMessage());
    }

    private URL writeConf(final Path work) throws IOException {
        return Files.writeString(work.resolve("doc.json"), DOC_CONTENT).toUri().toURL();
    }
}
