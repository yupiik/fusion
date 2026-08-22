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

import io.yupiik.fusion.framework.build.api.container.DetectableContext;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public class Elements {
    private final ProcessingEnvironment processingEnvironment;
    private final Map<TypeElement, Collection<ExecutableElement>> methodPerElement = new HashMap<>();
    private final Map<TypeElement, Collection<ExecutableElement>> hierarchyMethodsPerElement = new HashMap<>();
    private final Map<Element, Optional<? extends AnnotationMirror>> scopePerElement = new HashMap<>();
    private final Map<Class<?>, TypeElement> typeElementPerClass = new HashMap<>();
    private final Map<String, Boolean> comparablePerType = new HashMap<>();

    public Elements(final ProcessingEnvironment processingEnvironment) {
        this.processingEnvironment = processingEnvironment;
    }

    public Types getTypeUtils() {
        return processingEnvironment.getTypeUtils();
    }

    public TypeElement asElement(final Class<?> type) {
        return typeElementPerClass.computeIfAbsent(type, t -> (TypeElement) processingEnvironment.getTypeUtils().asElement(
                processingEnvironment.getElementUtils().getTypeElement(t.getName()).asType()));
    }

    public boolean isComparable(final TypeMirror type) {
        return comparablePerType.computeIfAbsent(type.toString(), k -> {
            final var types = processingEnvironment.getTypeUtils();
            final var mirror = types.asElement(type).asType();
            final var declaredType = types.getDeclaredType(asElement(Comparable.class), mirror);
            return types.isAssignable(type, declaredType);
        });
    }

    public Optional<? extends AnnotationMirror> findScopeAnnotation(final Element element) {
        return scopePerElement.computeIfAbsent(element, e -> e.getAnnotationMirrors().stream()
                .filter(ann -> ann.getAnnotationType().asElement().getAnnotation(DetectableContext.class) != null)
                .findFirst());
    }

    public Stream<ExecutableElement> findMethods(final TypeElement element) {
        return methodPerElement.computeIfAbsent(element, e -> ElementFilter.methodsIn(
                                processingEnvironment.getElementUtils().getAllMembers(e)).stream()
                        .filter(it -> {
                            if (it.getEnclosingElement() instanceof TypeElement te) {
                                return !Object.class.getName().equals(te.getQualifiedName().toString());
                            }
                            return true;
                        })
                        .toList())
                .stream();
    }

    /**
     * Resolves the callback methods carrying marker annotations across the type hierarchy in a single pass.
     * The most derived declaration of each signature decides whether the callback exists at all: an override
     * which does not carry the marker shadows and drops the inherited annotated callback.
     *
     * @return the annotated methods bucketed per requested marker, most derived declaration first.
     */
    public AnnotatedMethods findAnnotatedMethods(final TypeElement element, final TypeMirror... markers) {
        final var types = processingEnvironment.getTypeUtils();
        final var buckets = Stream.of(markers).map(m -> new ArrayList<ExecutableElement>()).toList();
        final var decidedSignatures = new HashSet<String>();
        for (final var method : markers.length == 0 ? List.<ExecutableElement>of() : hierarchyMethods(element).toList()) {
            // hierarchyMethods() is ordered most derived first so the first declaration seen for a
            // signature is the one deciding whether the callback exists at all
            if (!decidedSignatures.add(signature(method))) {
                continue;
            }
            final var annotationTypes = method.getAnnotationMirrors().stream()
                    .map(AnnotationMirror::getAnnotationType)
                    .toList();
            for (var i = 0; i < markers.length; i++) {
                final var marker = markers[i];
                if (annotationTypes.stream().anyMatch(t -> types.isSameType(t, marker))) {
                    buckets.get(i).add(method);
                }
            }
        }
        final var entries = new ArrayList<AnnotatedMethods.MarkerMethods>(markers.length);
        for (var i = 0; i < markers.length; i++) {
            entries.add(new AnnotatedMethods.MarkerMethods(markers[i], List.copyOf(buckets.get(i))));
        }
        return new AnnotatedMethods(types, List.copyOf(entries));
    }

    private String signature(final ExecutableElement method) {
        return method.getSimpleName() + method.getParameters().stream()
                .map(p -> p.asType().toString())
                .collect(joining(", ", "(", ")"));
    }

    public static final class AnnotatedMethods {
        private final Types types;
        private final List<MarkerMethods> entries;

        private AnnotatedMethods(final Types types, final List<MarkerMethods> entries) {
            this.types = types;
            this.entries = entries;
        }

        /**
         * @return the methods carrying {@code marker}, most derived declaration first per signature,
         * or an empty list when the marker was not requested.
         */
        public List<ExecutableElement> get(final TypeMirror marker) {
            return entries.stream()
                    .filter(it -> types.isSameType(it.marker(), marker))
                    .findFirst()
                    .map(MarkerMethods::methods)
                    .orElseGet(List::of);
        }

        public record MarkerMethods(TypeMirror marker, List<ExecutableElement> methods) {
        }
    }

    private Stream<ExecutableElement> hierarchyMethods(final TypeElement element) {
        return hierarchyMethodsPerElement.computeIfAbsent(element, e -> {
            // records/enums cannot extend another type and a direct child of Object has an empty
            // hierarchy: their declared methods are all we need
            if (e.getKind() == ElementKind.RECORD || e.getKind() == ElementKind.ENUM || isDirectChildOfObject(e)) {
                return List.copyOf(ElementFilter.methodsIn(e.getEnclosedElements()));
            }
            final var out = new ArrayDeque<ExecutableElement>();
            final var visited = new HashSet<String>();
            var current = Optional.of(e);
            while (current.isPresent()) {
                final var te = current.get();
                final var name = te.getQualifiedName().toString();
                if (!visited.add(name) || Object.class.getName().equals(name)) {
                    break;
                }
                ElementFilter.methodsIn(te.getEnclosedElements()).forEach(out::addLast);
                current = superclassOf(te);
            }
            return out.stream().toList();
        }).stream();
    }

    private boolean isDirectChildOfObject(final TypeElement element) {
        final var superclass = element.getSuperclass();
        if (superclass.getKind() != TypeKind.DECLARED) {
            return superclass.getKind() == TypeKind.NONE;
        }
        return superclass instanceof DeclaredType declared
                && declared.asElement() instanceof TypeElement te
                && Object.class.getName().equals(te.getQualifiedName().toString());
    }

    private Optional<TypeElement> superclassOf(final TypeElement element) {
        final var superclass = element.getSuperclass();
        if (superclass.getKind() != TypeKind.DECLARED || !(superclass instanceof DeclaredType declared)) {
            return Optional.empty();
        }
        return Optional.ofNullable(declared.asElement())
                .filter(TypeElement.class::isInstance)
                .map(TypeElement.class::cast);
    }
}
