package io.yupiik.fusion.framework.processor.generator;

import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.processor.Elements;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;
import java.util.function.Supplier;

public class ListenerGenerator extends BaseGenerator implements Supplier<BaseGenerator.GeneratedClass> {
    private final String packageName;
    private final String className;
    private final String suffix;
    private final String method;
    private final VariableElement param;
    private final Element enclosing;

    public ListenerGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                             final String packageName, final String className, final String suffix, final String method,
                             final VariableElement param, final Element enclosing) {
        super(processingEnv, elements);
        this.packageName = packageName;
        this.className = className;
        this.suffix = suffix;
        this.method = method;
        this.param = param;
        this.enclosing = enclosing;
    }

    @Override
    public GeneratedClass get() {
        final var priority = findPriority(param);
        final var eventType = param.asType().toString();

        final var out = new StringBuilder();
        if (!packageName.isBlank()) {
            out.append("package ").append(packageName).append(";\n\n");
        }

        appendGenerationVersion(out);
        out.append("public class ")
                .append(className).append(suffix)
                .append(" implements ").append(FusionListener.class.getName())
                .append('<').append(eventType).append("> {\n");
        out.append("  @Override\n");
        out.append("  public void onEvent(final ").append(RuntimeContainer.class.getName()).append(" container, final ").append(eventType).append(" event) {\n");
        out.append("    try (final var instance = container.lookup(").append(enclosing.asType().toString()).append(".class)) {\n");
        out.append("      instance.instance().").append(method).append("(event);\n");
        out.append("    }\n");
        out.append("  }\n");
        out.append("\n");
        out.append("  @Override\n");
        out.append("  public Class<").append(eventType).append("> eventType() {\n");
        out.append("    return ").append(eventType).append(".class;\n");
        out.append("  }\n");
        if (priority != 1000) {
            out.append("\n");
            out.append("  @Override\n");
            out.append("  public int priority() {\n");
            out.append("    return ").append(priority).append(";\n");
            out.append("  }\n");
        }
        out.append("}\n\n");

        return new GeneratedClass((!packageName.isBlank() ? packageName + '.' : "") + className + suffix, out.toString());
    }
}
