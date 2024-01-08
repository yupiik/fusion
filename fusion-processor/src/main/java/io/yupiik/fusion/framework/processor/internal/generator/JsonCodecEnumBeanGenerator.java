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
import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.json.internal.codec.EnumJsonCodec;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Name;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.ElementKind.ENUM_CONSTANT;

public class JsonCodecEnumBeanGenerator extends BaseGenerator implements Supplier<BaseGenerator.GeneratedClass> {
    private final String packageName;
    private final String className;
    private final TypeMirror type;

    public JsonCodecEnumBeanGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                                      final String packageName, final String className, final TypeMirror type) {
        super(processingEnv, elements);
        this.packageName = packageName;
        this.className = className;
        this.type = type;
    }

    @Override
    public GeneratedClass get() {
        final var pckPrefix = packageName.isBlank() ? "" : (packageName + '.');
        final var simpleName = className + "$" + FusionBean.class.getSimpleName();
        final var javaClassName = className.replace('$', '.');
        final var confBeanClassName = pckPrefix + simpleName;
        final var elements = ((DeclaredType) type).asElement().getEnclosedElements();
        final var toJsonFn = ElementFilter.methodsIn(elements).stream()
                .filter(it -> "toJsonString".equals(it.getSimpleName().toString()))
                .findAny()
                .isEmpty() ?
                "name()" : "toJsonString()";
        final var values = ElementFilter.fieldsIn(elements).stream()
                .filter(variableElement -> variableElement.getKind() == ENUM_CONSTANT)
                .map(VariableElement::getSimpleName)
                .map(Name::toString)
                .sorted()
                .toList();

        final var out = new StringBuilder();
        if (!packageName.isBlank()) {
            out.append("package ").append(packageName).append(";\n\n");
        }
        out.append("public class ").append(simpleName).append(" extends ")
                .append(BaseBean.class.getName()).append("<").append(EnumJsonCodec.class.getName()).append('<').append(pckPrefix).append(javaClassName).append(">> {\n");
        out.append("  public ").append(className).append('$').append(FusionBean.class.getSimpleName()).append("() {\n");
        out.append("    super(")
                .append("new ").append(Types.ParameterizedTypeImpl.class.getName().replace('$', '.'))
                .append("(").append(EnumJsonCodec.class.getName()).append(".class, ").append(javaClassName).append(".class), ")
                .append(DefaultScoped.class.getName()).append(".class, ") // will be a singleton in json mapper anyway
                .append("1000, ")
                .append(Map.class.getName()).append(".of());\n");
        out.append("  }\n");
        out.append("\n");
        out.append("  @Override\n");
        out.append("  public ").append(EnumJsonCodec.class.getName()).append('<').append(javaClassName).append("> create(final ").append(RuntimeContainer.class.getName())
                .append(" container, final ")
                .append(List.class.getName()).append("<").append(Instance.class.getName()).append("<?>> dependents) {\n");
        out.append("    return new ").append(EnumJsonCodec.class.getName()).append('<')
                .append(javaClassName).append(">(")
                .append(javaClassName).append(".class, ")
                .append(List.class.getName()).append(".of(")
                .append(values.stream().map(it -> javaClassName + "." + it).collect(joining(", ")))
                .append("), ")
                .append("v -> v.").append(toJsonFn)
                .append(");\n");
        out.append("  }\n");
        out.append("}\n\n");

        return new GeneratedClass(confBeanClassName, out.toString());
    }
}
