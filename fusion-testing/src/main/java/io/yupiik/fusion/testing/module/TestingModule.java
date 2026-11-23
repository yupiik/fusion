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
package io.yupiik.fusion.testing.module;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans local build directories for constructor-based legacy/manual descriptors when module discovery is insufficient.
 * Annotation-processor registrations use normal automatic module discovery and are not filtered by this fallback.
 * The {@code io.yupiik.fusion.testing.module.TestingModule.dirs} system property overrides the scanned directories
 * using the platform path separator.
 */
public class TestingModule extends ListingPredicateModule {
    public TestingModule() {
        super(
                name -> name.endsWith("$FusionBean.class") || name.contains("$FusionBean$"),
                name -> name.contains("$FusionListener$"),
                findDirs());
    }

    private static List<Path> findDirs() {
        final var prop = System.getProperty(TestingModule.class.getName() + ".dirs");
        if (prop != null && !prop.isBlank()) {
            return Stream.of(prop.split(Pattern.quote(File.pathSeparator)))
                    .map(String::strip)
                    .filter(Predicate.not(String::isBlank))
                    .map(Path::of)
                    .filter(Files::exists)
                    .toList();
        }
        final var current = Path.of(".");
        if (Files.exists(current.resolve("pom.xml"))) { // maven, resolve target/classes and target/test-classes
            return Stream.of(current.resolve("target/classes"), current.resolve("target/test-classes"))
                    .filter(Files::exists)
                    .toList();
        }
        if (Files.exists(current.resolve("build.gradle"))) { // gradle
            final var java = current.resolve("build/classes/java");
            if (Files.exists(java)) {
                try (final var list = Files.list(java)) {
                    return list.toList();
                } catch (final IOException e) {
                    // no-op, fallback on system prop
                }
            }
        }
        return List.of(current);
    }
}
