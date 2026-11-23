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
import io.yupiik.fusion.framework.build.api.lifecycle.Destroy;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.ParsedType;
import io.yupiik.fusion.framework.processor.internal.metadata.MetadataContributorRegistry;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import java.util.ArrayList;
import java.util.function.Supplier;

import static javax.lang.model.element.Modifier.STATIC;
import static javax.tools.Diagnostic.Kind.ERROR;

public class MethodBeanGenerator extends BaseGenerator implements Supplier<BaseGenerator.GeneratedClass> {
    private final String packageName;
    private final String beanClassName;
    private final ExecutableElement element;

    public MethodBeanGenerator(final ProcessingEnvironment processingEnvironment, final Elements elements,
                               final MetadataContributorRegistry metadataContributorRegistry,
                               final String packageName,
                               final String beanClassName, final ExecutableElement element) {
        super(processingEnvironment, elements, metadataContributorRegistry);
        this.packageName = packageName;
        this.beanClassName = beanClassName;
        this.element = element;
    }

    @Override
    public GeneratedClass get() {
        final var closeable = isAutocloseable(element.getReturnType());
        final var destroyAnnotation = element.getAnnotation(Destroy.class);
        if (destroyAnnotation != null && !closeable) {
            processingEnv.getMessager().printMessage(ERROR,
                    "@Destroy on a @Bean producer method requires the produced value to implement AutoCloseable to be closeable", element);
        }
        final var parsed = ParsedType.of(element.getReturnType());
        final var type = element.getReturnType().toString();
        final var registration = switch (parsed.type()) {
            case CLASS -> parsed.className() + ".class";
            case PARAMETERIZED_TYPE -> parsed.createParameterizedTypeImpl();
        };
        final var name = (packageName.isBlank() ? "" : packageName + '.') + beanClassName;
        final var signature = RuntimeContainer.class.getName() + " container, java.util.List<" + Instance.class.getName() + "<?>> dependents";
        final var targetType = processingEnv.getTypeUtils().erasure(element.getEnclosingElement().asType()).toString();
        final var target = element.getModifiers().contains(STATIC) ? targetType :
                "lookup(container, " + targetType + ".class, dependents)";
        final var methods = new ArrayList<Binding.Method>();
        methods.add(new Binding.Method(0,
                "private static " + type + " $method(" + signature + ") {\n    return " + target + '.' +
                        element.getSimpleName() + '(' + createExecutableInjections(element) + ");\n  }\n",
                ""));
        return new GeneratedClass(name, new Binding(packageName, "bean", FusionBean.class.getName() + "<?>",
                "new io.yupiik.fusion.framework.api.container.bean.FunctionalBean<>(" + registration + ", " +
                        findScope(element) + ".class, " + findPriority(element) + ", " +
                        (destroyAnnotation == null ? 0 : destroyAnnotation.order()) + ", " + metadata(element) + ", " + Binding.helperReference(0) +
                        ", null, null, null, " + closeable + ")", methods));
    }
}
