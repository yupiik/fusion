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

import io.yupiik.fusion.framework.build.api.metadata.BeanMetadataAlias;
import io.yupiik.fusion.framework.build.api.metadata.spi.MetadataContributor;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/** Reads and validates compile-time aliases without generating classes or resources. */
public class MetadataContributorGenerator implements Supplier<MetadataContributor> {
    private final Elements elements;
    private final TypeElement type;

    public MetadataContributorGenerator(final Elements elements, final TypeElement type) {
        this.elements = elements;
        this.type = type;
    }

    @Override
    public MetadataContributor get() {
        final var alias = type.getAnnotationMirrors().stream()
                .filter(it -> ((TypeElement) it.getAnnotationType().asElement()).getQualifiedName()
                        .contentEquals(BeanMetadataAlias.class.getName()))
                .findFirst();
        if (alias.isEmpty()) {
            return null;
        }
        final var values = elements.getElementValuesWithDefaults(alias.orElseThrow()).entrySet().stream()
                .collect(toMap(it -> it.getKey().getSimpleName().toString(), it -> (String) it.getValue().getValue()));
        final var defaultName = defaultValue(values.get("name"));
        final var defaultValue = defaultValue(values.get("value"));
        final var overrides = ElementFilter.methodsIn(elements.getAllMembers(type)).stream()
                .map(it -> it.getSimpleName().toString())
                .collect(toSet());
        if (defaultName.isBlank() && !overrides.contains("name")) {
            throw new IllegalArgumentException(type.getQualifiedName() + " does not define name(), either set it in @BeanMetadataAlias or define `String name()` method.");
        }
        if (defaultValue.isBlank() && !overrides.contains("value")) {
            throw new IllegalArgumentException(type.getQualifiedName() + " does not define value(), either set it in @BeanMetadataAlias or define `String value()` method.");
        }
        return new Alias(type.getQualifiedName().toString(), defaultName, defaultValue);
    }

    private String defaultValue(final String value) {
        return BeanMetadataAlias.UNSET.equals(value) ? "" : value;
    }

    private record Alias(String annotationType, String name, String value) implements MetadataContributor {
    }
}
