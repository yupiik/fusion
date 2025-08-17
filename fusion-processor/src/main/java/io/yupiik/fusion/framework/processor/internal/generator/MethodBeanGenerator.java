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
import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.ParsedType;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.Modifier.STATIC;

public class MethodBeanGenerator extends BaseGenerator implements Supplier<BaseGenerator.GeneratedClass> {
    private final String enclosingClass;
    private final String packageName;
    private final String beanClassName;
    private final ExecutableElement element;

    public MethodBeanGenerator(final ProcessingEnvironment processingEnvironment, final Elements elements,
                               final String enclosingClass, final String packageName,
                               final String beanClassName, final ExecutableElement element) {
        super(processingEnvironment, elements);
        this.enclosingClass = enclosingClass;
        this.packageName = packageName;
        this.beanClassName = beanClassName;
        this.element = element;
    }

    @Override
    public GeneratedClass get() {
        final var methodName = element.getSimpleName().toString();
        final boolean isStatic = element.getModifiers().contains(STATIC);
        final var scope = findScope(element);
        final int priority = findPriority(element);
        final var parsedType = ParsedType.of(element.getReturnType());
        final var injections = element.getParameters().isEmpty() ? null : createExecutableInjections(element);

        final var out = new StringBuilder();
        if (!packageName.isBlank()) {
            out.append("package ").append(packageName).append(";\n\n");
        }

        final var imports = new HashSet<String>();
        switch (parsedType.type()) {
            case CLASS -> {
                if (!parsedType.className().startsWith("java.lang.")) {
                    imports.add(parsedType.className());
                }
            }
            case PARAMETERIZED_TYPE -> {
                imports.add(Type.class.getName());
                imports.add("io.yupiik.fusion.framework.api.container.Types.ParameterizedTypeImpl");
                if (!parsedType.raw().startsWith("java.lang.")) {
                    imports.add(parsedType.raw());
                }
                imports.addAll(parsedType.args().stream().filter(it -> !it.startsWith("java.lang.")).toList());
            }
        }
        out.append(imports.stream()
                .sorted()
                .map(it -> "import " + it + ";")
                .collect(joining("\n", "", "\n\n")));

        final var type = switch (parsedType.type()) {
            case CLASS -> parsedType.simpleName(parsedType.className());
            case PARAMETERIZED_TYPE -> parsedType.args().stream()
                    .map(parsedType::simpleName)
                    .collect(joining(", ", parsedType.simpleName(parsedType.raw()) + "<", ">"));
        };

        appendGenerationVersion(out);
        out.append("public class ")
                .append(beanClassName)
                .append(" extends ").append(BaseBean.class.getName())
                .append('<').append(type).append("> {\n");
        out.append("  public ").append(beanClassName).append("() {\n");
        out.append("    super(");
        switch (parsedType.type()) {
            case CLASS -> out.append(type).append(".class");
            case PARAMETERIZED_TYPE -> out.append("new ")
                    .append(Types.ParameterizedTypeImpl.class.getName().replace('$', '.')).append("(")
                    .append(parsedType.simpleName(parsedType.raw())).append(".class").append(",")
                    .append(parsedType.args().stream()
                            .map(it -> parsedType.simpleName(it) + ".class")
                            .collect(joining(", ")))
                    .append(")");
        }
        out.append(", ")
                .append(scope).append(".class, ")
                .append(priority).append(", ")
                .append(metadata(element)).append(");\n");
        out.append("  }\n");
        out.append("\n");
        out.append("  @Override\n");
        if (!isStatic && injections != null && ( // and has some cast
                injections.contains("lookups(container, ") ||
                        injections.contains("(" + Optional.class.getName() + "<"))) {
            out.append("  @SuppressWarnings(\"unchecked\")\n");
        }
        out.append("  public ").append(type).append(" create(final ").append(RuntimeContainer.class.getName())
                .append(" container, final ")
                .append(List.class.getName()).append("<").append(Instance.class.getName()).append("<?>> dependents) {\n");
        if (isStatic) {
            out.append("    return ").append(enclosingClass);
        } else {
            out.append("    return lookup(container, ").append(enclosingClass).append(".class, dependents)\n");
        }
        out.append('.').append(methodName).append("(");
        if (injections != null) {
            out.append(injections);
        }
        out.append(");\n");
        out.append("  }\n");

        if (isAutocloseable(element.getReturnType())) {
            out.append("\n");
            out.append("  @Override\n");
            out.append("  public void destroy(final ").append(RuntimeContainer.class.getName())
                    .append(" container, final ").append(type).append(" instance) {\n");
            out.append("    try {\n");
            out.append("      instance.close();\n");
            out.append("    } catch (final Exception e) {\n");
            out.append("      throw new IllegalStateException(e);\n");
            out.append("    }\n");
            out.append("  }\n");
        }
        out.append("}\n\n");

        return new GeneratedClass((!packageName.isBlank() ? packageName + '.' : "") + beanClassName, out.toString());
    }
}
