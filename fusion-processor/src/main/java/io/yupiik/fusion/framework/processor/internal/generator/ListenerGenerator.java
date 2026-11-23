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

import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.processor.internal.Bean;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.metadata.MetadataContributorRegistry;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import java.util.List;
import java.util.function.Supplier;

import static java.util.stream.Collectors.joining;

public class ListenerGenerator extends BaseGenerator implements Supplier<BaseGenerator.GeneratedClass> {
    private final String packageName;
    private final String className;
    private final String suffix;
    private final ExecutableElement element;

    public ListenerGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                             final MetadataContributorRegistry metadataContributorRegistry,
                             final String packageName, final String className, final String suffix, final ExecutableElement element) {
        super(processingEnv, elements, metadataContributorRegistry);
        this.packageName = packageName;
        this.className = className;
        this.suffix = suffix;
        this.element = element;
    }

    @Override
    public GeneratedClass get() {
        final var method = element.getSimpleName().toString();
        final var params = element.getParameters();
        if (params.isEmpty()) {
            throw new IllegalArgumentException(element + " has no parameters, @OnEvent method must have at least one parameter (the event type)");
        }
        final var param = params.get(0);
        final var enclosing = element.getEnclosingElement();
        final var priority = findPriority(param);
        final var eventType = param.asType().toString();

        final var out = new StringBuilder(1024);
        final var injections = createExecutableFieldInjections(element.getParameters().stream().skip(1).toList()).toList();
        out.append("  private static void $method(final ").append(RuntimeContainer.class.getName()).append(" main__container, final ").append(eventType).append(" event) {\n");
        out.append("    try (final var root__instance = main__container.lookup(").append(enclosing.asType().toString()).append(".class)");
        if (!injections.isEmpty()) {
            out.append(injections.stream()
                    .map(it -> "final var " + it.name() + " = " + eventLookup(it))
                    .collect(joining(";\n         ", ";\n         ", "")));
        }
        out.append(") {\n");
        out.append("      root__instance.instance().").append(method).append("(event");
        if (!injections.isEmpty()) {
            out.append(injections.stream()
                    .map(Bean.FieldInjection::name)
                    .map(it -> it + ".instance()")
                    .collect(joining(", ", ", ", "")));
        }
        out.append(");\n");
        out.append("    }\n");
        out.append("  }\n");
        final var name = (!packageName.isBlank() ? packageName + '.' : "") + className + suffix;
        return new GeneratedClass(name, new Binding(packageName, "listener", FusionListener.class.getName() + "<?>",
                "new io.yupiik.fusion.framework.api.container.listener.FunctionalListener<" + eventType + ">(" + eventType + ".class, " + priority + ", " + Binding.helperReference(3) + ")",
                List.of(new Binding.Method(3, out.toString(), ""))));
    }
}
