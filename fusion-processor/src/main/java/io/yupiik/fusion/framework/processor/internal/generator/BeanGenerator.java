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
package io.yupiik.fusion.framework.processor.internal.generator;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.processor.internal.Bean;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.metadata.MetadataContributorRegistry;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.tools.Diagnostic.Kind.ERROR;

public class BeanGenerator extends BaseGenerator implements Supplier<BaseGenerator.GeneratedClass> {
    private final List<Bean.FieldInjection> injections;
    private final String packageName;
    private final String className;
    private final Element element;
    private final Map<String, String> data;
    private final TypeMirror init;
    private final TypeMirror destroy;

    public BeanGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                         final MetadataContributorRegistry metadataContributorRegistry,
                         final List<Bean.FieldInjection> injections, final String packageName,
                         final String className, final Element element, final Map<String, String> data,
                         final TypeMirror init, final TypeMirror destroy) {
        super(processingEnv, elements, metadataContributorRegistry);
        this.injections = injections;
        this.packageName = packageName;
        this.className = className;
        this.element = element;
        this.data = data;
        this.init = init;
        this.destroy = destroy;
    }

    @Override
    public BaseGenerator.GeneratedClass get() {
        final var scope = findScope(element);
        final int priority = findPriority(element);
        final var postConstruct = callMethodsWithMarker(element, init);
        final var preDestroy = callMethodsWithMarker(element, destroy);
        final var constructorInjections = constructorInjectionsFor(element);

        final var out = new StringBuilder();
        if (!packageName.isBlank()) {
            out.append("package ").append(packageName).append(";\n\n");
        }

        appendGenerationVersion(out);
        out.append("public class ")
                .append(className).append('$').append(FusionBean.class.getSimpleName())
                .append(" extends ").append(BaseBean.class.getName())
                .append('<').append(className.replace('$', '.')).append("> {\n");
        out.append("  public ").append(className).append('$').append(FusionBean.class.getSimpleName()).append("() {\n");
        out.append("    super(")
                .append(className.replace('$', '.')).append(".class, ")
                .append(scope).append(".class, ")
                .append(priority).append(", ")
                .append(metadata(element, data)).append(");\n");
        out.append("  }\n");
        out.append("\n");
        out.append("  @Override\n");
        if (constructorInjections != null && ( // and has some cast
                constructorInjections.contains("lookups(container, ") ||
                        constructorInjections.contains("(" + Optional.class.getName() + "<"))) {
            out.append("  @SuppressWarnings(\"unchecked\")\n");
        }
        out.append("  public ").append(className.replace('$', '.')).append(" create(final ").append(RuntimeContainer.class.getName())
                .append(" container, final ")
                .append(List.class.getName()).append("<").append(Instance.class.getName()).append("<?>> dependents) {\n");
        out.append("    final var instance = new ").append(className.replace('$', '.')).append("(")
                .append(constructorInjections == null ? "" : constructorInjections).append(");\n");
        out.append("    inject(container, dependents, instance);\n");
        out.append(postConstruct);
        out.append("    return instance;\n");
        out.append("  }\n");

        if (!injections.isEmpty()) {
            out.append("\n");
            out.append("  @Override\n");
            if (injections.stream().anyMatch(i -> i.list() || i.set() || i.optional() || i.type().toString().contains("<"))) {
                out.append("  @SuppressWarnings(\"unchecked\")\n");
            }
            out.append("  public void inject(final ").append(RuntimeContainer.class.getName()).append(" container, final ")
                    .append(List.class.getName()).append("<")
                    .append(Instance.class.getName()).append("<?>> dependents, final ")
                    .append(className.replace('$', '.')).append(" instance) {\n");
            out.append(injections.stream()
                    .map(this::setField)
                    .collect(joining("\n")));
            out.append("  }\n");
        }

        if (!preDestroy.isBlank()) {
            out.append("\n");
            out.append("  @Override\n");
            out.append("  public void destroy(final ").append(RuntimeContainer.class.getName())
                    .append(" container, final ").append(className.replace('$', '.')).append(" instance) {\n");
            out.append(preDestroy);
            out.append("  }\n");
        }

        out.append("}\n\n");

        return new GeneratedClass((!packageName.isBlank() ? packageName + '.' : "") + className + "$" + FusionBean.class.getSimpleName(), out.toString());
    }

    private String constructorInjectionsFor(final Element element) {
        if (!(element instanceof TypeElement te)) {
            return null;
        }
        return selectConstructor(te)
                .map(this::createExecutableInjections)
                .orElse("");
    }

    private String setField(final Bean.FieldInjection injection) {
        return "    instance." + injection.name() + " = " + injectionLookup(injection) + ";\n";
    }

    private String callMethodsWithMarker(final Element element, final TypeMirror marker) { // todo: support parent or better to use @Override?
        if (!(element instanceof TypeElement te)) {
            return "";
        }
        final var calls = findMethods(te, marker)
                .peek(e -> {
                    if (e.getModifiers().contains(PRIVATE)) {
                        processingEnv.getMessager().printMessage(ERROR, "Private methods are unsupported for now: '" +
                                e.getEnclosingElement() + "." + e + "'");
                    }
                })
                .map(m -> "    instance." + m.getSimpleName() + "();\n")
                .collect(joining("\n", "", ""));
        if (calls.isBlank()) {
            return "";
        }
        return calls + '\n';
    }
}
