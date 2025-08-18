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

import io.yupiik.fusion.framework.build.api.metadata.BeanMetadataAlias;
import io.yupiik.fusion.framework.build.api.metadata.spi.MetadataContributor;
import io.yupiik.fusion.framework.processor.internal.Elements;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toSet;

public class MetadataContributorGenerator extends BaseGenerator implements Supplier<MetadataContributorGenerator.ContributorOutput> {
    private static final String SUFFIX = "$MetadataContributor";

    private final String packageName;
    private final String className;
    private final BeanMetadataAlias alias;
    private final TypeElement type;

    public MetadataContributorGenerator(final ProcessingEnvironment processingEnv,
                                        final Elements elements,
                                        final String packageName,
                                        final String className,
                                        final BeanMetadataAlias alias,
                                        final TypeElement type) {
        super(processingEnv, elements, null);
        this.packageName = packageName;
        this.className = className;
        this.alias = alias;
        this.type = type;
    }

    @Override
    public ContributorOutput get() {
        final var packagePrefix = packageName == null || packageName.isBlank() ? "" : (packageName + '.');
        final var packageLine = packagePrefix.isBlank() ? "" : ("package " + packageName + ";\n\n");

        final var generatedClassName = className + SUFFIX;

        final var defaultName = alias.name().equals(BeanMetadataAlias.UNSET) ? "" : alias.name();
        final var defaultValue = alias.value().equals(BeanMetadataAlias.UNSET) ? "" : alias.value();

        final var overrides = ElementFilter.methodsIn(processingEnv.getElementUtils().getAllMembers(type)).stream()
                .filter(it -> it.getSimpleName().contentEquals("name") || it.getSimpleName().contentEquals("value"))
                .map(it -> it.getSimpleName().subSequence(0, it.getSimpleName().length()).toString())
                .collect(toSet());

        if (defaultName.isBlank() && !overrides.contains("name")) {
            throw new IllegalArgumentException(packagePrefix + className + " does not define name(), either set it in @BeanMetadataAlias or define `String name()` method.");
        }
        if (defaultValue.isBlank() && !overrides.contains("value")) {
            throw new IllegalArgumentException(packagePrefix + className + " does not define value(), either set it in @BeanMetadataAlias or define `String value()` method.");
        }

        final var fqn = packagePrefix + className;
        return new ContributorOutput(
                new GeneratedClass(
                        packagePrefix + generatedClassName,
                        packageLine +
                                generationVersion() +
                                "public class " + generatedClassName + " implements " + MetadataContributor.class.getName() + " {\n" +
                                "    @Override\n" +
                                "    public String name() {\n" +
                                "        return \"" + defaultName.replace("\"", "\\\"").replace("\n", "\\n") + '"' +
                                ";\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public String value() {\n" +
                                "        return \"" + defaultValue.replace("\"", "\\\"").replace("\n", "\\n") + '"' +
                                ";\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public String annotationType() {\n" +
                                "        return \"" + fqn + "\";\n" +
                                "    }\n" +
                                "}\n" +
                                "\n"),
                new MetadataContributor() {
                    @Override
                    public String name() {
                        return defaultName;
                    }

                    @Override
                    public String value() {
                        return defaultValue;
                    }

                    @Override
                    public String annotationType() {
                        return packagePrefix + className;
                    }
                });
    }

    public record ContributorOutput(GeneratedClass generatedClass, MetadataContributor contributor) {}
}
