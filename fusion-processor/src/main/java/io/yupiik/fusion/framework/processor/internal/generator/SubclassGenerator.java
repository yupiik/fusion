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

import io.yupiik.fusion.framework.api.container.context.subclass.DelegatingContext;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.ParsedType;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.tools.Diagnostic;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.DEFAULT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.TYPEVAR;

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
                            visibilityFrom(m.getModifiers()) +
                            templateTypes(m) +
                            m.getReturnType() + " " + methodName + "(" +
                            m.getParameters().stream().map(p -> p.asType() + " " + p.getSimpleName()).collect(joining(", ")) +
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

    private String exceptions(final ExecutableElement m) {
        final var types = m.getThrownTypes();
        if (types.isEmpty()) {
            return "";
        }
        return types.stream()
                .map(ParsedType::of)
                .filter(it -> it.type() == ParsedType.Type.CLASS)
                .map(ParsedType::className)
                .collect(joining(", ", "throws ", ""));
    }

    private String templateTypes(final ExecutableElement m) { // todo: enhance and make it even more recursive?
        final var templates = new LinkedHashSet<String>();

        final var alreadyHandled = new HashSet<TypeMirror>();
        final var result = m.getReturnType();
        if (isTemplate(result, alreadyHandled)) {
            alreadyHandled.add(result);
            templates.add(result + " " + templateBound((TypeVariable) result));
        } else if (result instanceof DeclaredType dt) {
            templates.addAll(dt.getTypeArguments().stream()
                    .filter(it -> isTemplate(it, alreadyHandled))
                    .map(it -> it + " " + templateBound((TypeVariable) it))
                    .toList());
        }

        templates.addAll(m.getParameters().stream()
                .map(VariableElement::asType)
                .filter(it -> isTemplate(it, alreadyHandled))
                .map(it -> it + " " + templateBound((TypeVariable) it))
                .toList());
        templates.addAll(m.getParameters().stream()
                .map(VariableElement::asType)
                .filter(it -> it instanceof DeclaredType)
                .map(it -> (DeclaredType) it)
                .flatMap(dt -> dt.getTypeArguments().stream())
                .filter(it -> isTemplate(it, alreadyHandled))
                .map(it -> it + " " + templateBound((TypeVariable) it))
                .toList());

        if (templates.isEmpty()) {
            return "";
        }
        return '<' + String.join(", ", templates) + "> ";
    }

    // todo: refine this
    private String templateBound(final TypeVariable type) {
        String out = "";

        final var lowerBound = type.getUpperBound() == null ? null : ParsedType.of(type.getLowerBound());
        if (lowerBound != null &&
                lowerBound.type() == ParsedType.Type.CLASS &&
                !"<nulltype>".equals(lowerBound.className()) &&
                !Object.class.getName().endsWith(lowerBound.className())) {
            out += " super " + lowerBound.className();
        }

        final var upperBound = type.getUpperBound() == null ? null : ParsedType.of(type.getUpperBound());
        if (upperBound != null &&
                upperBound.type() == ParsedType.Type.CLASS &&
                !"<nulltype>".equals(upperBound.className()) &&
                !Object.class.getName().endsWith(upperBound.className())) {
            out += " extends " + upperBound.className();
        }

        return out;
    }

    private boolean isTemplate(final TypeMirror type, final Set<TypeMirror> alreadyHandled) {
        return type.getKind() == TYPEVAR && type instanceof TypeVariable &&
                !"?".equals(type.toString()) && !"<none>".endsWith(type.toString()) &&
                alreadyHandled.add(type);
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
