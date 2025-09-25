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

import io.yupiik.fusion.cli.internal.BaseCliCommand;
import io.yupiik.fusion.cli.internal.CliCommand;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.build.api.cli.Command;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.meta.Docs;
import io.yupiik.fusion.framework.processor.internal.metadata.MetadataContributorRegistry;

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

public class CliCommandGenerator extends BaseGenerator implements Supplier<CliCommandGenerator.Output> {
    private static final String SUFFIX = "$FusionCliCommand";

    private final Command command;
    private final boolean beanForCliCommands;
    private final String packageName;
    private final String className;
    private final TypeElement type;
    private final Collection<Docs.ClassDoc> docs;

    public CliCommandGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                               final MetadataContributorRegistry metadataContributorRegistry,
                               final boolean beanForCliCommands, final String packageName,
                               final String className, final Command command, final TypeElement type,
                               final Collection<Docs.ClassDoc> docs) {
        super(processingEnv, elements, metadataContributorRegistry);
        this.command = command;
        this.beanForCliCommands = beanForCliCommands;
        this.packageName = packageName;
        this.className = className;
        this.type = type;
        this.docs = docs;
    }

    @Override
    public Output get() {
        final var packagePrefix = packageName == null || packageName.isBlank() ? "" : (packageName + '.');
        final var packageLine = packagePrefix.isBlank() ? "" : ("package " + packageName + ";\n\n");
        final var commandClassName = className + SUFFIX;
        final var constructorParameters = findConstructors(type)
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
                .orElseThrow(() -> new IllegalArgumentException("@Command classes must have at least one argument constructor with a @RootConfiguration parameter: " + packagePrefix + className))
                .getParameters();
        final var configurationElt = !constructorParameters.isEmpty() ? constructorParameters.get(0) : null;
        final var configurationType = configurationElt != null ? toFqnName(processingEnv.getTypeUtils().asElement(configurationElt.asType())) : null;

        if (constructorParameters.size() > 1 && !beanForCliCommands) {
            throw new IllegalArgumentException("@Command classes can only get injections if using a bean so ensure to set -Afusion.generateBeanForCliCommands in your compiler: " + packagePrefix + className);
        }

        final var hasInjections = beanForCliCommands && constructorParameters.size() != 1;

        final String metadata = metadata(type);
        return new Output(
                new GeneratedClass(packagePrefix + commandClassName, packageLine +
                        generationVersion() +
                        "public class " + commandClassName + " extends " + BaseCliCommand.class.getName() + (hasInjections ? ".ContainerBaseCliCommand" : "") +
                        "<" + (configurationType == null ? Void.class.getName() : configurationType.replace('$', '.')) + ", " + className.replace('$', '.') + "> {\n" +
                        "  public " + commandClassName + "(" + (hasInjections ? "final " + RuntimeContainer.class.getName() + " container" : "") + ") {\n" +
                        "    super(\n" +
                        "      \"" + command.name() + "\",\n" +
                        "      \"" + command.description().replace("\"", "\\\"").replace("\n", "\\n") + "\",\n" +
                        "      c -> " + (constructorParameters.isEmpty() ? "null" : "new " + configurationType + ConfigurationFactoryGenerator.SUFFIX + "(c).get()") + ",\n" +
                        "      (c, deps) -> new " + className.replace('$', '.') + "(" +
                        (configurationType == null ? "" : "c") +
                        (hasInjections ? constructorParameters.stream()
                                .skip(1) // configuration by convention
                                .map(param -> "lookup(container, " + toFqnName(processingEnv.getTypeUtils().asElement(param.asType())).replace('$', '.') + ".class, deps)")
                                .collect(joining(", ", configurationType == null ? "" : ", ", "")) : "") + ")," +
                        "      " + List.class.getName() + ".of(" + (configurationType == null ? "" : parameters(configurationType, null)) + "),\n" +
                        "      "+ metadata + ");\n" +
                        "  }\n" +
                        "}\n" +
                        "\n"),
                beanForCliCommands ?
                        new GeneratedClass(packagePrefix + commandClassName + '$' + FusionBean.class.getSimpleName(), packageLine +
                                generationVersion() +
                                "public class " + commandClassName + '$' + FusionBean.class.getSimpleName() + " extends " + BaseBean.class.getName() + "<" + commandClassName + "> {\n" +
                                "  public " + commandClassName + '$' + FusionBean.class.getSimpleName() + "() {\n" +
                                "    super(\n" +
                                "      " + commandClassName + ".class,\n" +
                                "      " + findScope(type) + ".class,\n" +
                                "      " + findPriority(type) + ",\n" +
                                "      " + metadata + ");\n" +
                                "  }\n" +
                                "\n" +
                                "  @Override\n" +
                                "  public " + commandClassName + " create(final " + RuntimeContainer.class.getName() + " container, final " +
                                List.class.getName() + "<" + Instance.class.getName() + "<?>> dependents) {\n" +
                                "    return new " + commandClassName + "(" + (hasInjections ? "container" : "") + ");\n" +
                                "  }\n" +
                                "}\n" +
                                "\n") :
                        null);
    }

    // assume args are in this form:
    // $ --<conf name> <value>
    // Note: we support "-" for the root configuration key which enables to drop the prefix
    private String parameters(final String configurationType, final String parentPrefix) {
        return doFindParameters(configurationType, parentPrefix).collect(joining(", "));
    }

    private Stream<String> doFindParameters(final String configurationType, final String parentPrefix) {
        return findConf(configurationType)
                .mapMulti((it, out) -> {
                    if (it.ref() != null) {
                        final var prefix = (parentPrefix == null ? "" : (parentPrefix + '.')) + it.name();
                        doFindParameters(it.ref(), prefix).forEach(out);
                    } else {
                        out.accept(parameter(parentPrefix, it));
                    }
                });
    }

    private static String parameter(final String parentPrefix, final Docs.DocItem item) {
        final var javaName = (parentPrefix == null ? "" : (parentPrefix + '.')) + item.name();
        final var cliName = (javaName.startsWith("-.") ? "" : "--") + javaName.replace('.', '-');
        return "new " + CliCommand.Parameter.class.getName().replace('$', '.') + "(" +
                "\"" + javaName.replace("\"", "\\\"").replace("\n", "\\n") + "\", " +
                "\"" + cliName.replace("\"", "\\\"").replace("\n", "\\n") + "\", " +
                "\"" + item.doc().replace("\"", "\\\"").replace("\n", "\\n") + "\")";
    }

    private Stream<Docs.DocItem> findConf(final String configurationType) {
        return docs.stream()
                .filter(it -> configurationType.equals(it.name()))
                .flatMap(it -> it.items().stream())
                .sorted(comparing(Docs.DocItem::name));
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

    public record Output(BaseGenerator.GeneratedClass command, BaseGenerator.GeneratedClass bean) {
    }
}
