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

import io.yupiik.fusion.cli.internal.BaseCliCommand;
import io.yupiik.fusion.cli.internal.CliCommand;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.build.api.cli.Command;
import io.yupiik.fusion.framework.build.api.persistence.Table;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.meta.Docs;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.ElementKind.PACKAGE;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;

public class PersistenceEntityGenerator extends BaseGenerator implements Supplier<PersistenceEntityGenerator.Output> {
    private static final String SUFFIX = "$FusionPersistenceEntity";

    private final Table table;
    private final boolean beanForPersistenceEntities;
    private final String packageName;
    private final String className;
    private final TypeElement type;
    private final Collection<Docs.ClassDoc> docs;

    public PersistenceEntityGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                                      final boolean beanForPersistenceEntities, final String packageName,
                                      final String className, final Table table, final TypeElement type,
                                      final Collection<Docs.ClassDoc> docs) {
        super(processingEnv, elements);
        this.table = table;
        this.beanForPersistenceEntities = beanForPersistenceEntities;
        this.packageName = packageName;
        this.className = className;
        this.type = type;
        this.docs = docs;
    }

    @Override
    public Output get() {
        final var packagePrefix = packageName == null || packageName.isBlank() ? "" : (packageName + '.');
        final var packageLine = packagePrefix.isBlank() ? "" : ("package " + packageName + ";\n\n");
        final var tableClassName = className + SUFFIX;
        final var constructorParameters = findConstructors(type)
                .filter(e -> e.getParameters().size() >= 1)
                .max(comparing(e -> {
                    if (e.getModifiers().contains(PUBLIC)) {
                        return 1000;
                    }
                    if (e.getModifiers().contains(PROTECTED)) {
                        return 100;
                    }
                    if (!e.getModifiers().contains(PRIVATE)) { // package scope
                        return 10;
                    }
                    return 0; // private
                }))
                .orElseThrow(() -> new IllegalArgumentException("@Table classes must have at least one argument constructor with a @RootConfiguration parameter: " + packagePrefix + className))
                .getParameters();
        final var configurationElt = constructorParameters.get(0);
        final var configurationType = toFqnName(processingEnv.getTypeUtils().asElement(configurationElt.asType()));

        if (constructorParameters.size() > 1 && !beanForPersistenceEntities) {
            throw new IllegalArgumentException("@Table classes can only get injections if using a bean so ensure to set -Afusion.generateBeanForPersistenceEntities in your compiler: " + packagePrefix + className);
        }

        final var hasInjections = beanForPersistenceEntities && constructorParameters.size() != 1;

        return new Output(
                new GeneratedClass(packagePrefix + tableClassName, packageLine +
                        generationVersion() +
                        "public class " + tableClassName +
                        "<" + className.replace('$', '.') + "> {\n" +
                        "  public " + tableClassName + "(" + (hasInjections ? "final " + RuntimeContainer.class.getName() + " container" : "") + ") {\n" +
                        "    super(\n" +
                        "      \"" + table.value() + "\",\n" +
                        "      c -> new " + configurationType + ConfigurationFactoryGenerator.SUFFIX + "(c).get(),\n" +
                        "      (c, deps) -> new " + className.replace('$', '.') + "(c" +
                        (hasInjections ? constructorParameters.stream()
                                .skip(1) // configuration by convention
                                .map(param -> "lookup(container, " + toFqnName(processingEnv.getTypeUtils().asElement(param.asType())) + ".class, deps)")
                                .collect(joining(", ", ", ", "")) : "") + ")," +
                        "      " + List.class.getName() + ".of());\n" +
                        "  }\n" +
                        "}\n" +
                        "\n"),
                beanForPersistenceEntities ?
                        new GeneratedClass(packagePrefix + tableClassName + '$' + FusionBean.class.getSimpleName(), packageLine +
                                generationVersion() +
                                "public class " + tableClassName + '$' + FusionBean.class.getSimpleName() + " extends " + BaseBean.class.getName() + "<" + tableClassName + "> {\n" +
                                "  public " + tableClassName + '$' + FusionBean.class.getSimpleName() + "() {\n" +
                                "    super(\n" +
                                "      " + tableClassName + ".class,\n" +
                                "      " + findScope(type) + ".class,\n" +
                                "      " + findPriority(type) + ",\n" +
                                "      " + Map.class.getName() + ".of());\n" +
                                "  }\n" +
                                "\n" +
                                "  @Override\n" +
                                "  public " + tableClassName + " create(final " + RuntimeContainer.class.getName() + " container, final " +
                                List.class.getName() + "<" + Instance.class.getName() + "<?>> dependents) {\n" +
                                "    return new " + tableClassName + "(" + (hasInjections ? "container" : "") + ");\n" +
                                "  }\n" +
                                "}\n" +
                                "\n") :
                        null);
    }

    // todo: share in Types?
    private String toFqnName(final Element mirror) {
        final var builder = new StringBuilder();
        var current = mirror;
        while (current != null) {
            if (current.getKind() == PACKAGE) {
                final var pck = current instanceof PackageElement pe ? pe.getQualifiedName().toString() : current.toString();
                return pck + (pck.isBlank() ? "" : ".") + builder;
            }

            final var name = current.getSimpleName().toString();
            builder.insert(0, name + (builder.isEmpty() ? "" : "$"));
            current = current.getEnclosingElement();
        }
        return builder.toString();
    }

    public record Output(GeneratedClass command, GeneratedClass bean) {
    }
}
