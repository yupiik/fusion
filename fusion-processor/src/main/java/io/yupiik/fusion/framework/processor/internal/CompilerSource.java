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
package io.yupiik.fusion.framework.processor.internal;

import com.sun.source.util.Trees;
import org.eclipse.jdt.internal.compiler.apt.model.TypeElementImpl;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Optional compiler source locations. An absent URI is not evidence that a type is a dependency. */
public interface CompilerSource {
    Optional<String> uri(Element element);

    static CompilerSource of(final ProcessingEnvironment environment) {
        return CompilerSourceFactory.of(ModuleProcessingEnvironment.unwrap(environment));
    }

    static Optional<String> sourceUri(final JavaFileObject file) {
        // getFileObjectOf also returns binary reference representations. They are not source origins.
        return file == null || file.getKind() != JavaFileObject.Kind.SOURCE ? Optional.empty() :
                Optional.of(file.toUri().toString());
    }

    final class Ecj implements CompilerSource {
        @Override
        public Optional<String> uri(final Element element) {
            TypeElement outermost = null;
            for (var current = element; current != null; current = current.getEnclosingElement()) {
                if (current instanceof TypeElement type) {
                    outermost = type;
                }
            }
            if (!(outermost instanceof TypeElementImpl ecj)) {
                return Optional.empty();
            }
            try {
                // ECJ's getFileObjectOf calls a Java 18 default method; use its filename API on Java 17.
                final var name = ecj.getFileName();
                if (name == null || !name.endsWith(JavaFileObject.Kind.SOURCE.extension)) {
                    return Optional.empty();
                }
                final var path = Path.of(name);
                return Files.isRegularFile(path) ? Optional.of(path.toUri().toString()) : Optional.empty();
            } catch (final UnsupportedOperationException unavailable) {
                return Optional.empty();
            }
        }
    }

    final class Javac implements CompilerSource {
        private final Trees trees;

        public Javac(final ProcessingEnvironment environment) {
            Trees integration;
            try {
                integration = Trees.instance(environment);
            } catch (final IllegalArgumentException | LinkageError unavailable) {
                integration = null;
            }
            trees = integration;
        }

        @Override
        public Optional<String> uri(final Element element) {
            if (trees == null) {
                return Optional.empty();
            }
            for (var current = element; current != null; current = current.getEnclosingElement()) {
                final var path = trees.getPath(current);
                if (path != null) {
                    return sourceUri(path.getCompilationUnit().getSourceFile());
                }
                if (current instanceof TypeElement) {
                    break;
                }
            }
            return Optional.empty();
        }
    }
}
