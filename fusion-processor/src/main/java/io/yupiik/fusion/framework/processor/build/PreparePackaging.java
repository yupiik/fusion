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
package io.yupiik.fusion.framework.processor.build;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

public final class PreparePackaging {
    private PreparePackaging() {
        // no-op
    }

    public static void main(final String... args) throws IOException {
        final var classes = Path.of(args[0]);
        final var newOutBase = classes.resolve("fusion/annotationprocessor/precompiled");
        Files.walkFileTree(classes, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                final var relative = classes.relativize(file).toString().replace(File.separatorChar, '/');
                if (file.getFileName().toString().endsWith(".class") && relative.contains("/internal/")) {
                    final var target = newOutBase.resolve(relative + 'F' /* don't let the IDE think it is a class */);
                    Files.createDirectories(target.getParent());
                    Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return super.visitFile(file, attrs);
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                try (final var list = Files.list(dir)) {
                    if (list.count() == 0) {
                        Files.delete(dir);
                    }
                }
                return super.postVisitDirectory(dir, exc);
            }
        });
    }
}
