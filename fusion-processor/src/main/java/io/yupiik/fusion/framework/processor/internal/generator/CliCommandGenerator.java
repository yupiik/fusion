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

import io.yupiik.fusion.cli.internal.CliCommand;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.build.api.cli.Command;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.meta.Docs;
import io.yupiik.fusion.framework.processor.internal.metadata.MetadataContributorRegistry;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
    private Map<String, List<Docs.DocItem>> docItemsByName;

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

        final var paramsResult = configurationType == null ? null : parameters(configurationType, null);

        final var path = command.name();
        validatePath(path, packagePrefix + className);
        final var pathLiteral = "new String[]{" +
                java.util.Arrays.stream(path).map(segment -> "\"" + escaped(segment) + "\"").collect(joining(", ")) + "}";

        final var id = packagePrefix + commandClassName;
        final var adapter = "io.yupiik.fusion.cli.internal.BaseCliCommand";
        final var conf = configurationElt == null ? "java.lang.Void" : configurationElt.asType().toString();
        final var commandType = type.getQualifiedName().toString();
        final var methods = new ArrayList<Binding.Method>();
        methods.add(new Binding.Method(5,
                "private static io.yupiik.fusion.cli.internal.CliCommand<?> $method(io.yupiik.fusion.framework.api.RuntimeContainer container) { return new " + adapter + "<" + conf + ", " + commandType + ">(" + pathLiteral + ", \"" + escaped(command.description()) + "\", java.util.List.of(" +
                        (paramsResult == null ? "" : paramsResult.parameters()) + "), " + metadata(type) + ", " + (paramsResult == null ? "\"\"" : paramsResult.cliHelpContent()) +
                        ", $callback{8}, " + (configurationType == null ? "null" : "$callback{6}") + ", (configuration, deps) -> $helper{7}(configuration, container, deps)); }", ""));
        if (configurationType != null) {
            methods.add(new Binding.Method(6,
                    "private static " + conf + " $method(io.yupiik.fusion.framework.api.configuration.Configuration configuration, java.util.function.Function<String, String> keyMapper) { return " +
                            Binding.reference(configurationType + ConfigurationFactoryGenerator.SUFFIX) + ".apply(configuration, keyMapper); }", ""));
        }
        methods.add(new Binding.Method(7,
                "private static " + commandType + " $method(" + conf + " configuration, io.yupiik.fusion.framework.api.RuntimeContainer container, java.util.List<io.yupiik.fusion.framework.api.Instance<?>> deps) { return new " + commandType + "(" +
                        (configurationType == null ? "" : "configuration") +
                        (hasInjections ? constructorParameters.stream().skip(1).map(param -> "lookup(container, " + processingEnv.getTypeUtils().erasure(param.asType()) + ".class, deps)")
                                .collect(joining(", ", configurationType == null ? "" : ", ", "")) : "") + "); }", ""));
        methods.add(new Binding.Method(8,
                paramsResult == null ? "private static String $method(String key) { return \"--\" + key.replace('.', '-'); }" : paramsResult.keyMapperContent().replace("keyMapper(", "$method("), ""));
        final var artifacts = new ArrayList<Binding.Artifact>();
        artifacts.add(new Binding.Artifact(id, new Binding(packageName, null,
                "java.util.function.Function<io.yupiik.fusion.framework.api.RuntimeContainer, io.yupiik.fusion.cli.internal.CliCommand<?>>", "(java.util.function.Function<io.yupiik.fusion.framework.api.RuntimeContainer, io.yupiik.fusion.cli.internal.CliCommand<?>>) " + Binding.helperReference(5), methods)));
        final var beanId = id + ".FusionBean";
        if (beanForCliCommands) {
            artifacts.add(new Binding.Artifact(beanId, new Binding(packageName, "bean", FusionBean.class.getName() + "<?>",
                    "new io.yupiik.fusion.framework.api.container.bean.FunctionalBean<>(new io.yupiik.fusion.framework.api.container.Types.ParameterizedTypeImpl(io.yupiik.fusion.cli.internal.CliCommand.class, " + commandType + ".class), " + findScope(type) + ".class, " + findPriority(type) + ", 0, " + metadata(type) + ", " + Binding.helperReference(0) + ", null, null, null, false)",
                    List.of(Binding.Method.callback(0,
                            "private static io.yupiik.fusion.cli.internal.CliCommand<?> $method(io.yupiik.fusion.framework.api.RuntimeContainer container, java.util.List<io.yupiik.fusion.framework.api.Instance<?>> dependents) { return " + Binding.reference(id) + ".apply(container); }",
                            "(container, dependents) -> " + Binding.reference(id) + ".apply(container)")))));
        }
        return new Output(new GeneratedClass(id, null, artifacts), beanForCliCommands ? beanId : null);
    }

    // assume args are in this form:
    // $ --<conf name> <value>
    // Note: "-" root configurations drop the prefix at generation time so keys are already plain here
    private ParametersResult parameters(final String configurationType, final String parentPrefix) {
        final var keyMapperCases = new StringBuilder();
        final var helpBuilder = new StringBuilder();
        final var cmdPrefix = "--" + String.join("-", command.name()) + "-";
        final var params = doFindParameters(configurationType, parentPrefix, keyMapperCases, helpBuilder, cmdPrefix)
                .collect(joining(", "));
        final var defaultBranch = "return \"--\" + key.replace('.', '-');";
        final var helpText = "  private static String keyMapper(final String key) {\n" +
                "    switch (key) {\n" +
                keyMapperCases +
                "      default: " + defaultBranch + "\n" +
                "    }\n" +
                "  }";
        final var cliHelpContent = helpBuilder.isEmpty() ? "\"\"" :
                "\"\\nAvailable parameters:\\n\" + \n    " +
                        helpBuilder.toString().lines()
                                .map(line -> "\"" + escaped(line) + "\\n\"")
                                .collect(joining(" + \n    ")) +
                        " + \n    \"\\n\"";
        return new ParametersResult(params, helpText, cliHelpContent);
    }

    private static void validatePath(final String[] path, final String className) {
        if (path == null || path.length == 0) {
            throw new IllegalArgumentException("@Command must define at least one name segment: " + className);
        }
        for (final var segment : path) {
            if (segment == null || segment.isBlank()) {
                throw new IllegalArgumentException("@Command name segments cannot be blank: " + className);
            }
            if (segment.startsWith("-")) {
                throw new IllegalArgumentException("@Command name segments cannot start with '-': " + className);
            }
        }
    }

    private record ParametersResult(String parameters, String keyMapperContent, String cliHelpContent) {
    }

    private Stream<String> doFindParameters(final String configurationType, final String parentPrefix,
                                            final StringBuilder keyMapperCases, final StringBuilder helpBuilder,
                                            final String cmdPrefix) {
        return findConf(configurationType)
                .mapMulti((it, out) -> {
                    if (it.ref() != null) {
                        final var prefix = (parentPrefix == null ? "" : (parentPrefix + '.')) + it.name();
                        doFindParameters(it.ref(), prefix, keyMapperCases, helpBuilder, cmdPrefix).forEach(out);
                    } else {
                        out.accept(parameter(parentPrefix, it, cmdPrefix, keyMapperCases, helpBuilder));
                    }
                });
    }

    private static String parameter(final String parentPrefix, final Docs.DocItem item,
                                    final String cmdPrefix, final StringBuilder keyMapperCases,
                                    final StringBuilder helpBuilder) {
        final var javaName = (parentPrefix == null ? "" : (parentPrefix + '.')) + item.name();
        final var cliName = "--" + javaName.replace('.', '-');
        final var displayName = cliName.startsWith(cmdPrefix) ? "--" + cliName.substring(cmdPrefix.length()) : cliName;
        keyMapperCases.append("      case \"").append(escaped(javaName)).append("\": return \"").append(escaped(cliName)).append("\";\n");
        // raw text, the escaping is done once when the java literal is emitted (cliHelpContent)
        helpBuilder.append(displayName).append(": ").append(item.doc() == null ? "" : item.doc()).append('\n');
        return "new " + CliCommand.Parameter.class.getName().replace('$', '.') + "(" +
                "\"" + escaped(javaName) + "\", " +
                "\"" + escaped(cliName) + "\", " +
                "\"" + escaped(item.doc()) + "\", " +
                "\"" + escaped(item.type()) + "\", " +
                "\"" + escaped(item.defaultValue()) + "\")";
    }

    // escapes a raw value once for its embedding in a generated java string literal
    private static String escaped(final String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    private Stream<Docs.DocItem> findConf(final String configurationType) {
        if (docItemsByName == null) { // indexed once, doFindParameters() re-enters for each nested configuration
            docItemsByName = new HashMap<>();
            for (final var doc : docs) {
                docItemsByName.computeIfAbsent(doc.name(), k -> new ArrayList<>()).addAll(doc.items());
            }
            for (final var items : docItemsByName.values()) {
                items.sort(comparing(Docs.DocItem::name));
            }
        }
        return docItemsByName.getOrDefault(configurationType, List.of()).stream();
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

    public record Output(BaseGenerator.GeneratedClass command, String beanClassName) {
    }
}
