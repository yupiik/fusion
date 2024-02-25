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

import io.yupiik.fusion.framework.api.container.context.subclass.DelegatingContext;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.ParsedType;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;

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
        final boolean useConstructorSuperNull = findNoArgConstructor().isEmpty();

        final var out = new StringBuilder();
        if (!packageName.isBlank()) {
            out.append("package ").append(packageName).append(";\n\n");
        }

        final var templates = new LinkedHashSet<String>();
        final var alreadyHandled = new HashSet<TypeMirror>();
        final var result = (DeclaredType) typeElement.asType();
        final var typeArgs = result.getTypeArguments();
        final var templatesDef = templates(typeArgs, result, alreadyHandled, templates);

        appendGenerationVersion(out);
        out.append("class ").append(className).append(DELEGATING_CLASS_SUFFIX).append(templatesDef)
                .append(" extends ").append(className.replace('$', '.'))
                .append(templatesDef.isBlank() ? "" : templateNamesOnly(templatesDef))
                .append(" {\n");
        out.append("  private final ").append(DelegatingContext.class.getName()).append("<").append(className.replace('$', '.')).append("> fusionContext;\n");
        out.append("\n");
        out.append("  ").append(className).append(DELEGATING_CLASS_SUFFIX)
                .append("(final ").append(DelegatingContext.class.getName()).append("<").append(className.replace('$', '.')).append("> context) {\n");
        if (useConstructorSuperNull) {
            out.append("    super(")
                    .append(selectConstructor(typeElement)
                            .map(it -> it.getParameters().stream()
                                    .map(p -> {
                                        try {
                                            final var type = ParsedType.of(p.asType());
                                            if (type.type() != ParsedType.Type.CLASS) {
                                                return "null";
                                            }
                                            return switch (type.className()) {
                                                case "boolean" -> "false";
                                                case "double" -> "0.";
                                                case "float" -> "0.f";
                                                case "long" -> "0L";
                                                case "integer" -> "0";
                                                case "short" -> "(short) 0";
                                                case "byte" -> "(byte) 0";
                                                default -> "null";
                                            };
                                        } catch (final RuntimeException re) {
                                            return "null";
                                        }
                                    })
                                    .collect(joining(", ")))
                            .orElse(""))
                    .append(");\n");
        }
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
                            (modifiers.contains(PUBLIC) ||
                                    (modifiers.contains(PROTECTED) &&
                                            m.getEnclosingElement() instanceof TypeElement te &&
                                            te.getQualifiedName().contentEquals(typeElement.getQualifiedName())));
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
                    final var contextualMethod = (ExecutableType) elements.getTypeUtils().asMemberOf((DeclaredType) typeElement.asType(), m);
                    final var paramIt = m.getParameters().iterator();
                    return "@Override\n" +
                            visibilityFrom(m.getModifiers()) +
                            templateTypes(contextualMethod, typeArgs) +
                            contextualMethod.getReturnType() + " " + methodName + "(" +
                            contextualMethod.getParameterTypes().stream().map(p -> p + " " + paramIt.next().getSimpleName()).collect(joining(", ")) +
                            ")" + exceptions(m) + " {\n" +
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

    private String templateNamesOnly(final String templatesDef) {
        var current = templatesDef;
        current = dropFromTemplate(current, " extends ");
        current = dropFromTemplate(current, " super ");
        return current;
    }

    private String dropFromTemplate(final String current, final String marker) {
        final int next = current.indexOf(marker);
        if (next < 0) {
            return current;
        }
        final int end1 = current.indexOf(",", next);
        final int end2 = current.indexOf(">", next);
        return dropFromTemplate(
                current.substring(0, next) + current.substring(IntStream.of(end1, end2).filter(it -> it > 0).findFirst().orElse(next)),
                marker);
    }

    private Optional<ExecutableElement> findNoArgConstructor() {
        return findConstructors(typeElement)
                .filter(it -> it.getModifiers().contains(PROTECTED) || it.getModifiers().contains(PUBLIC))
                .filter(it -> it.getParameters().isEmpty())
                .findAny();
    }
}
