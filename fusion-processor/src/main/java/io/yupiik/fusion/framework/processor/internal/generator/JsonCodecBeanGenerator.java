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
package io.yupiik.fusion.framework.processor.internal.generator;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.processor.internal.Elements;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class JsonCodecBeanGenerator extends BaseGenerator implements Supplier<BaseGenerator.GeneratedClass> {
    private final String packageName;
    private final String className;

    public JsonCodecBeanGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                                  final String packageName, final String className) {
        super(processingEnv, elements);
        this.packageName = packageName;
        this.className = className;
    }

    @Override
    public GeneratedClass get() {
        final var pckPrefix = packageName.isBlank() ? "" : (packageName + '.');
        final var simpleName = className + "$" + FusionBean.class.getSimpleName();
        final var confBeanClassName = pckPrefix + simpleName;

        final var out = new StringBuilder();
        if (!packageName.isBlank()) {
            out.append("package ").append(packageName).append(";\n\n");
        }
        out.append("public class ").append(simpleName).append(" extends ")
                .append(BaseBean.class.getName()).append("<").append(pckPrefix).append(className).append("> {\n");
        out.append("  public ").append(className).append('$').append(FusionBean.class.getSimpleName()).append("() {\n");
        out.append("    super(")
                .append(className).append(".class, ")
                .append(DefaultScoped.class.getName()).append(".class, ") // will be a singleton in json mapper anyway
                .append("1000, ")
                .append(Map.class.getName()).append(".of());\n");
        out.append("  }\n");
        out.append("\n");
        out.append("  @Override\n");
        out.append("  public ").append(className).append(" create(final ").append(RuntimeContainer.class.getName())
                .append(" container, final ")
                .append(List.class.getName()).append("<").append(Instance.class.getName()).append("<?>> dependents) {\n");
        out.append("    return new ").append(pckPrefix).append(className).append("();\n");
        out.append("  }\n");
        out.append("}\n\n");

        return new GeneratedClass(confBeanClassName, out.toString());
    }
}
