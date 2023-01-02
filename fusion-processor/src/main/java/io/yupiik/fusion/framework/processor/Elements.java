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
package io.yupiik.fusion.framework.processor;

import io.yupiik.fusion.framework.build.api.container.DetectableContext;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import java.util.Optional;
import java.util.stream.Stream;

public class Elements {
    private final ProcessingEnvironment processingEnvironment;

    public Elements(final ProcessingEnvironment processingEnvironment) {
        this.processingEnvironment = processingEnvironment;
    }

    public Optional<? extends AnnotationMirror> findScopeAnnotation(final Element element) {
        return element.getAnnotationMirrors().stream()
                .filter(ann -> ann.getAnnotationType().asElement().getAnnotation(DetectableContext.class) != null)
                .findFirst();
    }

    public Stream<ExecutableElement> findMethods(final TypeElement element) {
        return ElementFilter.methodsIn(processingEnvironment.getElementUtils().getAllMembers(element)).stream()
                .filter(it -> {
                    if (it.getEnclosingElement() instanceof TypeElement te) {
                        return !Object.class.getName().equals(te.getQualifiedName().toString());
                    }
                    return true;
                });
    }
}
