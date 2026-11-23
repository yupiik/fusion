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
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.build.api.lifecycle.Destroy;
import io.yupiik.fusion.framework.processor.internal.Bean;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.metadata.MetadataContributorRegistry;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.tools.Diagnostic.Kind.ERROR;

public class BeanGenerator extends BaseGenerator implements Supplier<BaseGenerator.GeneratedClass> {
    private final List<Bean.FieldInjection> injections;
    private final String packageName;
    private final String className;
    private final Element element;
    private final Map<String, String> data;
    private final TypeMirror init;
    private final TypeMirror destroy;

    public BeanGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                         final MetadataContributorRegistry metadataContributorRegistry,
                         final List<Bean.FieldInjection> injections, final String packageName,
                         final String className, final Element element, final Map<String, String> data,
                         final TypeMirror init, final TypeMirror destroy) {
        super(processingEnv, elements, metadataContributorRegistry);
        this.injections = injections;
        this.packageName = packageName;
        this.className = className;
        this.element = element;
        this.data = data;
        this.init = init;
        this.destroy = destroy;
    }

    @Override
    public BaseGenerator.GeneratedClass get() {
        final var scope = findScope(element);
        final int priority = findPriority(element);
        final var lifecycle = element instanceof TypeElement te ?
                elements.findAnnotatedMethods(
                        te,
                        m -> m.getAnnotation(io.yupiik.fusion.framework.build.api.scanning.Bean.class) == null,
                        init,
                        destroy) : null;
        final var postConstruct = lifecycle == null ? "" : callMethodsWithMarker(lifecycle.get(init));
        final var preDestroy = lifecycle == null ? "" : callMethodsWithMarker(lifecycle.get(destroy));
        final var destroyOrder = lifecycle == null || lifecycle.get(destroy).isEmpty() ?
                0 : optionalDestroyOrder(lifecycle.get(destroy).get(0));
        final var constructorInjections = constructorInjectionsFor(element);

        final var type = ((TypeElement) element).getQualifiedName().toString();
        final var signature = RuntimeContainer.class.getName() + " container, java.util.List<" +
                Instance.class.getName() + "<?>> dependents";
        final var fields = injections.stream().map(this::setField).collect(joining("\n"));
        final var methods = new ArrayList<Binding.Method>();
        final var constructorDeclaration = "private static " + type + " $method(" + signature + ") {\n" +
                "    return new " + type + "(" + (constructorInjections == null ? "" : constructorInjections) + ");\n  }\n";
        methods.add(constructorInjections == null || constructorInjections.isBlank() ?
                Binding.Method.callback(0, constructorDeclaration, "(container, dependents) -> new " + type + "()") :
                new Binding.Method(0, constructorDeclaration));
        if (!injections.isEmpty()) {
            methods.add(new Binding.Method(1,
                    "private static void $method(" + signature + ", " + type + " instance) {\n" + fields + "  }\n", ""));
        }
        if (!postConstruct.isBlank()) {
            methods.add(new Binding.Method(5,
                    "private static void $method(" + RuntimeContainer.class.getName() + " container, " + type + " instance) {\n" + postConstruct + "  }\n", ""));
        }
        if (!preDestroy.isBlank()) {
            methods.add(new Binding.Method(2,
                    "private static void $method(" + RuntimeContainer.class.getName() + " container, " + type + " instance) {\n" + preDestroy + "  }\n", ""));
        }
        final var name = (!packageName.isBlank() ? packageName + '.' : "") + className + "$" + FusionBean.class.getSimpleName();
        final var factory = "new io.yupiik.fusion.framework.api.container.bean.FunctionalBean<" + type + ">(" +
                type + ".class, " + scope + ".class, " + priority + ", " + destroyOrder + ", " +
                metadata(element, data) + ", " + Binding.helperReference(0) + ", " +
                (injections.isEmpty() ? "null" : Binding.helperReference(1)) + ", " +
                (postConstruct.isBlank() ? "null" : Binding.helperReference(5)) + ", " +
                (preDestroy.isBlank() ? "null" : Binding.helperReference(2)) + ", false)";
        return new GeneratedClass(name, new Binding(packageName, "bean", FusionBean.class.getName() + "<?>", factory, methods));
    }

    private String constructorInjectionsFor(final Element element) {
        if (!(element instanceof TypeElement te)) {
            return null;
        }
        return selectConstructor(te)
                .map(this::createExecutableInjections)
                .orElse("");
    }

    private String setField(final Bean.FieldInjection injection) {
        return "    instance." + injection.name() + " = " + injectionLookup(injection) + ";\n";
    }

    private String callMethodsWithMarker(final List<ExecutableElement> methods) {
        final var calls = methods.stream()
                .peek(e -> {
                    if (e.getModifiers().contains(PRIVATE)) {
                        processingEnv.getMessager().printMessage(ERROR, "Private methods are unsupported for now: '" +
                                e.getEnclosingElement() + "." + e + "'");
                    }
                })
                .map(m -> "    instance." + m.getSimpleName() + "();\n")
                .collect(joining("\n", "", ""));
        if (calls.isBlank()) {
            return "";
        }
        return calls + '\n';
    }

    private int optionalDestroyOrder(final ExecutableElement method) {
        return ofNullable(method.getAnnotation(Destroy.class)).map(Destroy::order).orElse(0);
    }
}
