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
