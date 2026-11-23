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

import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.metadata.MetadataContributorRegistry;
import io.yupiik.fusion.json.internal.codec.EnumJsonCodec;
import io.yupiik.fusion.json.serialization.JsonCodec;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Name;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.ElementKind.ENUM_CONSTANT;

public class JsonCodecEnumBeanGenerator extends BaseGenerator implements Supplier<BaseGenerator.GeneratedClass> {
    private final String packageName;
    private final String className;
    private final TypeMirror type;

    public JsonCodecEnumBeanGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                                      final MetadataContributorRegistry metadataContributorRegistry,
                                      final String packageName, final String className, final TypeMirror type) {
        super(processingEnv, elements, metadataContributorRegistry);
        this.packageName = packageName;
        this.className = className;
        this.type = type;
    }

    @Override
    public GeneratedClass get() {
        final var prefix = packageName.isBlank() ? "" : packageName + '.';
        final var model = type.toString();
        final var beanId = prefix + className + "$" + FusionBean.class.getSimpleName();
        final var codecId = prefix + className + JsonCodecGenerator.SUFFIX;
        final var members = ((DeclaredType) type).asElement().getEnclosedElements();
        final var conversion = ElementFilter.methodsIn(members).stream()
                .anyMatch(it -> "toJsonString".contentEquals(it.getSimpleName())) ? "toJsonString" : "name";
        final var values = ElementFilter.fieldsIn(members).stream()
                .filter(it -> it.getKind() == ENUM_CONSTANT).map(VariableElement::getSimpleName)
                .map(Name::toString).sorted().map(it -> model + "." + it).collect(joining(", "));
        final var codec = new Binding(packageName, null, JsonCodec.class.getName() + "<" + model + ">",
                "new " + EnumJsonCodec.class.getName() + "<>(" + model + ".class, java.util.List.of(" + values +
                        "), " + model + "::" + conversion + ")", List.of());
        final var artifacts = new ArrayList<Binding.Artifact>();
        artifacts.add(new Binding.Artifact(codecId, codec));
        if (Boolean.parseBoolean(processingEnv.getOptions().getOrDefault("fusion.generateBeanForJsonCodec", "true"))) {
            artifacts.add(new Binding.Artifact(beanId, JsonCodecGenerator.codecBean(packageName, model, codecId)));
        }
        return new GeneratedClass(beanId, null, artifacts);
    }
}
