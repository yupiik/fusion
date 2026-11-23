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
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.metadata.MetadataContributorRegistry;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import java.util.List;
import java.util.function.Supplier;

public class BeanConfigurationGenerator extends BaseGenerator implements Supplier<BaseGenerator.GeneratedClass> {
    private final String packageName;
    private final String className;
    private final Element element;

    public BeanConfigurationGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                                      final MetadataContributorRegistry metadataContributorRegistry,
                                      final String packageName, final String className, final Element element) {
        super(processingEnv, elements, metadataContributorRegistry);
        this.packageName = packageName;
        this.className = className;
        this.element = element;
    }

    @Override
    public GeneratedClass get() {
        final var pckPrefix = packageName.isBlank() ? "" : (packageName + '.');
        final var simpleName = className + "$RootConfiguration$" + FusionBean.class.getSimpleName();
        final var confBeanClassName = pckPrefix + simpleName;

        final var type = processingEnv.getTypeUtils().erasure(element.asType()).toString();
        return new GeneratedClass(confBeanClassName, new Binding(packageName, "bean",
                FusionBean.class.getName() + "<?>",
                "new io.yupiik.fusion.framework.api.container.bean.FunctionalBean<>(" +
                        type + ".class, " + findScope(element) + ".class, " + findPriority(element) + ", 0, " +
                        metadata(element) + ", " + Binding.helperReference(0) + ", null, null, null, false)",
                List.of(new Binding.Method(0,
                        "private static " + type + " $method(final " + RuntimeContainer.class.getName() +
                                " container, final java.util.List<" + Instance.class.getName() + "<?>> dependents) {\n" +
                                "final var conf = lookup(container, " + Configuration.class.getName() + ".class, dependents);\n" +
                                "return " + Binding.reference(pckPrefix + className + ConfigurationFactoryGenerator.SUFFIX) +
                                ".apply(conf, java.util.function.Function.identity());\n}",
                        ""))));
    }
}
