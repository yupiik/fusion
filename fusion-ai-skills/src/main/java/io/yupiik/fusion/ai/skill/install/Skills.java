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
package io.yupiik.fusion.ai.skill.install;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the bundled {@code skills} tree from the classpath, whether it lives on the filesystem (development /
 * plain jar) or inside a fat jar (shaded runtime). No temporary extraction: jar entries are streamed directly.
 */
final class Skills {
    private Skills() {
        // no-op
    }

    /**
     * @return the list of {@code (skillName, content)} from the bundled skills, {@code none} when absent.
     */
    static List<Skill> bundled() {
        final var classLoader = Thread.currentThread().getContextClassLoader();
        final var skills = new ArrayList<Skill>();
        try {
            final var urls = classLoader.getResources("skills/");
            while (urls.hasMoreElements()) {
                final var url = urls.nextElement();
                if ("file".equals(url.getProtocol())) {
                    final var root = Path.of(url.toURI());
                    if (Files.isDirectory(root)) {
                        try (final var paths = Files.walk(root)) {
                            final var it = paths.iterator();
                            while (it.hasNext()) {
                                final var p = it.next();
                                if (Files.isRegularFile(p) && p.getFileName().toString().equals("SKILL.md")) {
                                    final var relative = root.relativize(p);
                                    final var skillName = relative.getName(0).toString();
                                    skills.add(file(skillName, p));
                                }
                            }
                        }
                    }
                } else if ("jar".equals(url.getProtocol())) {
                    readJar(url, skills);
                }
            }
            return skills;
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void readJar(final java.net.URL url, final List<Skill> skills) throws Exception {
        final var spec = url.toURI().toString();
        final var bang = spec.indexOf("!/");
        final var jarUri = (bang < 0 ? spec : spec.substring(0, bang)).replace("jar:", "");
        try (final var jar = new java.util.jar.JarFile(Path.of(java.net.URI.create(jarUri)).toFile())) {
            final var prefix = "skills/";
            final var entries = jar.entries();
            while (entries.hasMoreElements()) {
                final var entry = entries.nextElement();
                final var name = entry.getName();
                if (name.startsWith(prefix) && name.endsWith("/SKILL.md") && !entry.isDirectory()) {
                    final var relative = name.substring(prefix.length());
                    final var skillName = relative.substring(0, relative.indexOf('/'));
                    try (final var in = jar.getInputStream(entry)) {
                        skills.add(new Skill(skillName, new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                    }
                }
            }
        }
    }

    private static Skill file(final String skillName, final Path source) {
        try {
            return new Skill(skillName, Files.readString(source));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    record Skill(String name, String content) {
    }
}