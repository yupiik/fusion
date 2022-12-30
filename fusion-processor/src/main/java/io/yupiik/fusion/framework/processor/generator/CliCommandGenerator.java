package io.yupiik.fusion.framework.processor.generator;

import io.yupiik.fusion.cli.internal.BaseCliCommand;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.build.api.cli.Command;
import io.yupiik.fusion.framework.processor.Elements;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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

    public CliCommandGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                               final boolean beanForCliCommands, final String packageName,
                               final String className, final Command command, final TypeElement type) {
        super(processingEnv, elements);
        this.command = command;
        this.beanForCliCommands = beanForCliCommands;
        this.packageName = packageName;
        this.className = className;
        this.type = type;
    }

    @Override
    public Output get() {
        final var packagePrefix = packageName == null || packageName.isBlank() ? "" : (packageName + '.');
        final var packageLine = packagePrefix.isBlank() ? "" : ("package " + packageName + ";\n\n");
        final var commandClassName = className + SUFFIX;
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
                .orElseThrow(() -> new IllegalArgumentException("@Command classes must have at least one argument constructor with a @RootConfiguration parameter: " + packagePrefix + className))
                .getParameters();
        final var configurationElt = constructorParameters.get(0);
        final var configurationType = toFqnName(processingEnv.getTypeUtils().asElement(configurationElt.asType()));

        if (constructorParameters.size() > 1 && !beanForCliCommands) {
            throw new IllegalArgumentException("@Command classes can only get injections if using a bean so ensure to set -Afusion.generateBeanForCliCommands in your compiler: " + packagePrefix + className);
        }

        final var hasInjections = beanForCliCommands && constructorParameters.size() != 1;

        return new Output(
                new GeneratedClass(packagePrefix + commandClassName, packageLine +
                        generationVersion() +
                        "public class " + commandClassName + " extends " + BaseCliCommand.class.getName() + (hasInjections ? ".ContainerBaseCliCommand" : "") +
                        "<" + configurationType.replace('$', '.') + ", " + className.replace('$', '.') + "> {\n" +
                        "  public " + commandClassName + "(" + (hasInjections ? "final " + RuntimeContainer.class.getName() + " container" : "") + ") {\n" +
                        "    super(\n" +
                        "      \"" + command.name() + "\",\n" +
                        "      \"" + command.description().replace("\"", "\\\"").replace("\n", "\\n") + "\",\n" +
                        "      c -> new " + configurationType + ConfigurationFactoryGenerator.SUFFIX + "(c).get(),\n" +
                        "      (c, deps) -> new " + className.replace('$', '.') + "(c" +
                        (hasInjections ? constructorParameters.stream()
                                .skip(1) // configuration by convention
                                .map(param -> "lookup(container, " + toFqnName(processingEnv.getTypeUtils().asElement(param.asType())) + ".class, deps)")
                                .collect(joining(", ", ", ", "")) : "") + "));\n" +
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
                                "      " + Map.class.getName() + ".of());\n" +
                                "  }\n" +
                                "\n" +
                                "  @Override\n" +
                                "  public " + commandClassName + " create(final " + RuntimeContainer.class.getName() + " container, final " +
                                List.class.getName() + "<" + Instance.class.getName() + "<?>> dependents) {\n" +
                                "    return new " + commandClassName + "(container);\n" +
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

    public record Output(BaseGenerator.GeneratedClass command, BaseGenerator.GeneratedClass bean) {
    }
}
