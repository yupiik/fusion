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
import io.yupiik.fusion.framework.api.container.Generation;
import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.build.api.order.Order;
import io.yupiik.fusion.framework.processor.internal.Bean;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.ParsedType;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.type.TypeKind.TYPEVAR;

public abstract class BaseGenerator {
    protected static final Comparator<ExecutableElement> RECORD_CONSTRUCTOR_COMPARATOR = comparing(e -> {
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
    });

    protected final ProcessingEnvironment processingEnv;
    protected final Elements elements;

    private final TypeElement comparable;
    private final TypeMirror autocloseable;

    protected BaseGenerator(final ProcessingEnvironment processingEnv, final Elements elements) {
        this.processingEnv = processingEnv;
        this.elements = elements;
        this.comparable = asElement(processingEnv, Comparable.class);
        this.autocloseable = asElement(processingEnv, AutoCloseable.class).asType();
    }

    protected String visibilityFrom(final Set<Modifier> modifiers) {
        if (modifiers.contains(PROTECTED)) {
            return "protected ";
        }
        if (modifiers.contains(PUBLIC)) {
            return "public ";
        }
        return "";
    }

    protected void appendGenerationVersion(final StringBuilder out) {
        out.append(generationVersion());
    }

    protected String generationVersion() {
        return "@" + Generation.class.getName() + "(version = 1)\n";
    }

    protected boolean isComparable(final TypeMirror type) {
        final var types = processingEnv.getTypeUtils();
        final var mirror = types.asElement(type).asType();
        final var declaredType = types.getDeclaredType(comparable, mirror);
        return types.isAssignable(type, declaredType);
    }

    protected Stream<ExecutableElement> findMethods(final TypeElement element, final TypeMirror marker) {
        return elements.findMethods(element)
                .filter(it -> it.getAnnotationMirrors().stream()
                        .anyMatch(a -> processingEnv.getTypeUtils().isSameType(a.getAnnotationType(), marker)));
    }

    protected int findPriority(final Element element) {
        return ofNullable(element.getAnnotation(Order.class)).map(Order::value).orElse(1000);
    }

    protected String findScope(final Element element) {
        return elements.findScopeAnnotation(element)
                .map(AnnotationMirror::getAnnotationType)
                .map(DeclaredType::toString)
                .orElseGet(DefaultScoped.class::getName);
    }

    protected boolean isAutocloseable(final TypeMirror type) {
        return processingEnv.getTypeUtils().isAssignable(type, autocloseable);
    }

    protected TypeElement asElement(final ProcessingEnvironment processingEnv, final Class<?> type) {
        return (TypeElement) processingEnv.getTypeUtils().asElement(
                processingEnv.getElementUtils().getTypeElement(type.getName()).asType());
    }

    protected Optional<ExecutableElement> selectConstructor(final TypeElement te) {
        return findConstructors(te)
                .filter(e -> !e.getParameters().isEmpty())
                // we select the most visible constructor (public wins over protected for ex)
                // and if multiple the one with the most parameters
                // considering others are convenient constructors
                .max(Comparator.<ExecutableElement, Integer>comparing(e -> {
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
                        })
                        .thenComparing(e -> e.getParameters().size()));
    }

    protected Stream<ExecutableElement> findConstructors(final TypeElement te) {
        return te.getEnclosedElements().stream()
                .filter(it -> it.getKind() == CONSTRUCTOR)
                .map(ExecutableElement.class::cast);
    }

    protected String createExecutableInjections(final ExecutableElement it) {
        return createExecutableFieldInjections(it.getParameters())
                .map(this::injectionLookup)
                .collect(joining(", "));
    }

    protected Stream<Bean.FieldInjection> createExecutableFieldInjections(final List<? extends VariableElement> parameters) {
        return parameters.stream()
                .map(param -> {
                    final TypeMirror type;
                    final boolean list;
                    final boolean set;
                    final boolean optional;
                    if (param.asType() instanceof DeclaredType dt && dt.getTypeArguments().size() == 1) {
                        type = dt.getTypeArguments().get(0);

                        final var typeStr = dt.toString();
                        list = typeStr.startsWith(List.class.getName() + "<");
                        set = typeStr.startsWith(Set.class.getName() + "<");
                        optional = typeStr.startsWith(Optional.class.getName() + "<");
                    } else {
                        list = false;
                        set = false;
                        optional = false;
                        type = param.asType();
                    }
                    return new Bean.FieldInjection(
                            param.getSimpleName().toString(),
                            type,
                            list,
                            set,
                            optional,
                            param.getModifiers());
                });
    }

    protected String instanceTypeOf(final Bean.FieldInjection injection) {
        if (injection.optional()) { // using generic we need to type the left hand operator
            return Optional.class.getName() + "<" + injection.type() + ">";
        }
        if (injection.list()) { // using generic we need to type the left hand operator
            return List.class.getName() + "<" + injection.type() + ">";
        }
        if (injection.set()) { // using generic we need to type the left hand operator
            return Set.class.getName() + "<" + injection.type() + ">";
        }
        // other cases can be handled with var
        return "var";
    }

    protected String injectionLookup(final Bean.FieldInjection injection) {
        final var parsed = ParsedType.of(injection.type());
        if (injection.list()) { // only supports classes (but take care of parameterized ones)
            final var clazz = switch (parsed.type()) {
                case CLASS -> parsed.className();
                case PARAMETERIZED_TYPE -> parsed.raw();
            };
            return "(" + instanceTypeOf(injection) + ") " +
                    "lookups(container, " + clazz + ".class, instances -> " +
                    String.join("",
                            "instances.stream()",
                            isComparable(injection.type()) ?
                                    ".map(" + Instance.class.getName() + "::instance).sorted()" :
                                    ".sorted(java.util.Comparator.comparing(i -> i.bean().priority())).map(" + Instance.class.getName() + "::instance)",
                            ".map(" + switch (parsed.type()) {
                                case CLASS -> parsed.className() + ".class::cast";
                                case PARAMETERIZED_TYPE -> "it -> " + parsed.createParameterizedTypeCast() + " it";
                            } + ").collect(" + Collectors.class.getName() + ".toList())") +
                    ", dependents)";
        }
        if (injection.set()) { // only supports classes
            final var clazz = switch (parsed.type()) {
                case CLASS -> parsed.className();
                case PARAMETERIZED_TYPE -> parsed.raw();
            };
            return "(" + instanceTypeOf(injection) + ") lookups(container, " + clazz + ".class, " + Collectors.class.getName() + "toSet(), dependents)";
        }
        if (injection.optional()) { // only supports classes - todo: cache type
            return "(" + instanceTypeOf(injection) + ") " +
                    "lookup(container, new " + Types.ParameterizedTypeImpl.class.getName().replace('$', '.') + "(" +
                    Optional.class.getName() + ".class, " + injection.type() + ".class), " +
                    "dependents)";
        }

        return switch (parsed.type()) {
            case CLASS -> "lookup(container, " + parsed.className() + ".class, dependents)";
            case PARAMETERIZED_TYPE -> "(" +
                    parsed.raw() +
                    parsed.args().stream().map(parsed::simpleName).map(it -> it + ".class").collect(joining(",", "<", ">")) + ") " +
                    "lookup(container, " + parsed.createParameterizedTypeImpl() + ", dependents)";
        };
    }

    protected String eventLookup(final Bean.FieldInjection injection) { // outside a bean
        if (injection.list()) { // only supports classes
            return "main__container.lookups(" +
                    injection.type() + ".class, instances -> " +
                    String.join("",
                            "instances.stream()",
                            isComparable(injection.type()) ?
                                    ".map(" + Instance.class.getName() + "::instance).sorted()" :
                                    ".sorted(java.util.Comparator.comparing(i -> i.bean().priority())).map(" + Instance.class.getName() + "::instance)",
                            ".map(" + injection.type() + ".class::cast).collect(" + Collectors.class.getName() + ".toList())") +
                    ")";
        }
        if (injection.set()) { // only supports classes
            return "main__container.lookups(" + injection.type() + ".class, " + Collectors.class.getName() + "toSet())";
        }
        if (injection.optional()) { // only supports classes - todo: cache type
            return "(Instance<" + instanceTypeOf(injection) + ">) " +
                    "main__container.lookup(container, new " + Types.ParameterizedTypeImpl.class.getName().replace('$', '.') + "(" +
                    Optional.class.getName() + ".class, " + injection.type() + ".class))";
        }

        final var parsed = ParsedType.of(injection.type());
        return switch (parsed.type()) {
            case CLASS -> "main__container.lookup(" + parsed.className() + ".class)";
            case PARAMETERIZED_TYPE -> "(Instance<" +
                    parsed.raw() +
                    parsed.args().stream().map(parsed::simpleName).map(it -> it + ".class").collect(joining(",", "<", ">")) + ">) " +
                    "main__container.lookup(" +
                    "new " + Types.ParameterizedTypeImpl.class.getName().replace('$', '.') + "(" +
                    parsed.raw() + ".class, " + parsed.args().stream()
                    .map(parsed::simpleName)
                    .map(it -> it + ".class")
                    .collect(joining(",")) + ")";
        };
    }

    protected String exceptions(final ExecutableElement m) {
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

    // todo: enhance and make it even more recursive?
    protected String templateTypes(final ExecutableType m, final Collection<? extends TypeMirror> classOnes) {
        final var templates = new LinkedHashSet<String>();
        final var alreadyHandled = new HashSet<TypeMirror>();
        final var result = m.getReturnType();
        return templates(
                m.getParameterTypes().stream()
                        .filter(it -> classOnes == null || !classOnes.contains(it))
                        .toList(),
                result, alreadyHandled, templates);
    }

    protected String templates(final List<? extends TypeMirror> params, final TypeMirror result,
                               final Set<TypeMirror> alreadyHandled, final Set<String> templates) {
        if (isTemplate(result, alreadyHandled)) {
            alreadyHandled.add(result);
            templates.add(result + " " + templateBound((TypeVariable) result));
        } else if (result instanceof DeclaredType dt) {
            templates.addAll(dt.getTypeArguments().stream()
                    .filter(it -> isTemplate(it, alreadyHandled))
                    .map(it -> it + " " + templateBound((TypeVariable) it))
                    .toList());
        }

        templates.addAll(params.stream()
                .filter(it -> isTemplate(it, alreadyHandled))
                .map(it -> it + " " + templateBound((TypeVariable) it))
                .toList());
        templates.addAll(params.stream()
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
    protected String templateBound(final TypeVariable type) {
        String out = "";

        final var lowerBound = type.getUpperBound() == null ? null : ParsedType.of(type.getLowerBound());
        if (lowerBound != null &&
                lowerBound.type() == ParsedType.Type.CLASS &&
                !"null".equals(lowerBound.className()) && // ECJ
                !"<nulltype>".equals(lowerBound.className()) &&
                !Object.class.getName().endsWith(lowerBound.className())) {
            out += " super " + lowerBound.className();
        }

        final var upperBound = type.getUpperBound() == null ? null : ParsedType.of(type.getUpperBound());
        if (upperBound != null &&
                upperBound.type() == ParsedType.Type.CLASS &&
                !"null".equals(upperBound.className()) && // ECJ
                !"<nulltype>".equals(upperBound.className()) &&
                !Object.class.getName().endsWith(upperBound.className())) {
            out += " extends " + upperBound.className();
        }

        return out;
    }

    protected boolean isTemplate(final TypeMirror type, final Set<TypeMirror> alreadyHandled) {
        return type.getKind() == TYPEVAR && type instanceof TypeVariable &&
                !"?".equals(type.toString()) && !"<none>".endsWith(type.toString()) &&
                alreadyHandled.add(type);
    }

    public record GeneratedClass(String name, String content) {
    }
}
