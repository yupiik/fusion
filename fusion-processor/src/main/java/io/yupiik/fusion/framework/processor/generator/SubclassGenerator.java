package io.yupiik.fusion.framework.processor.generator;

import io.yupiik.fusion.framework.api.container.context.subclass.DelegatingContext;
import io.yupiik.fusion.framework.processor.Elements;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import java.util.function.Supplier;

import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.DEFAULT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

public class SubclassGenerator extends BaseGenerator implements Supplier<BaseGenerator.GeneratedClass> {
    private static final String DELEGATING_CLASS_SUFFIX = "$FusionSubclass";

    private final String packageName;
    private final String className;
    private final TypeElement typeElement;

    public SubclassGenerator(final ProcessingEnvironment processingEnvironment,
                             final Elements elements,
                             final String packageName, final String className, final TypeElement te) {
        super(processingEnvironment, elements);
        this.packageName = packageName;
        this.className = className;
        this.typeElement = te;
    }

    @Override
    public GeneratedClass get() {
        selectConstructor(typeElement).ifPresent(e -> ensureDefaultNoArgConstructor());

        final var out = new StringBuilder();
        if (!packageName.isBlank()) {
            out.append("package ").append(packageName).append(";\n\n");
        }

        appendGenerationVersion(out);
        out.append("class ").append(className).append(DELEGATING_CLASS_SUFFIX).append(" extends ").append(className.replace('$', '.')).append(" {\n");
        out.append("  private final ").append(DelegatingContext.class.getName()).append("<").append(className.replace('$', '.')).append("> fusionContext;\n");
        out.append("\n");
        out.append("  ").append(className).append(DELEGATING_CLASS_SUFFIX)
                .append("(final ").append(DelegatingContext.class.getName()).append("<").append(className.replace('$', '.')).append("> context) {\n");
        out.append("    this.fusionContext = context;\n");
        out.append("  }\n");
        out.append(elements.findMethods(typeElement)
                .filter(m -> {
                    final var modifiers = m.getModifiers();
                    final var name = m.getSimpleName().toString();
                    return !"<cinit>".equals(name) &&
                            !"<init>".equals(name) && // not the constructor - for now at least
                            !modifiers.contains(FINAL) &&
                            !modifiers.contains(STATIC) &&
                            !modifiers.contains(ABSTRACT) &&
                            !modifiers.contains(DEFAULT) /*more for later for interfaces*/ &&
                            // other methods can't be subclasses/overriden like this
                            (modifiers.contains(PUBLIC) || modifiers.contains(PROTECTED));
                })
                .map(m -> {
                    // note: if we start supporting interceptors,
                    //       then we would enhance the context API with a shouldIntercept("methodName"[, signature]) or alike
                    final var methodName = m.getSimpleName().toString();
                    final var args = m.getParameters().isEmpty() ?
                            "" :
                            m.getParameters().stream()
                                    .map(VariableElement::getSimpleName)
                                    .map(Name::toString)
                                    .collect(joining(", "));
                    return "@Override\n" +
                            visibilityFrom(m.getModifiers()) + m.getReturnType() + " " + methodName + "(" +
                            m.getParameters().stream().map(p -> p.asType() + " " + p.getSimpleName()).collect(joining(", ")) +
                            ") {\n" +
                            (m.getReturnType().getKind() == TypeKind.VOID ?
                                    "  this.fusionContext.instance()." + methodName + "(" + args + ");\n" :
                                    "  return this.fusionContext.instance()." + methodName + "(" + args + ");\n") +
                            "}\n";
                })
                .map(it -> it.indent(2))
                .collect(joining("\n", "\n", "")));
        out.append("}\n\n");

        return new GeneratedClass((packageName.isBlank() ? "" : (packageName + '.')) + className + DELEGATING_CLASS_SUFFIX, out.toString());
    }

    private void ensureDefaultNoArgConstructor() {
        findConstructors(typeElement)
                .filter(it -> it.getModifiers().contains(PROTECTED) || it.getModifiers().contains(PUBLIC))
                .filter(it -> it.getParameters().isEmpty())
                .findAny()
                .orElseGet(() -> {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "Subclasses can only be generated if there is a no-arg constructor in protected or public scope. " +
                                    "Ensure to define one in '" + typeElement + "'.");
                    return null;
                });
    }
}
