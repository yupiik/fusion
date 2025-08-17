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
package io.yupiik.fusion.framework.processor.internal.metadata;

import io.yupiik.fusion.framework.build.api.metadata.spi.MetadataContributor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

public class MetadataContributorRegistry {
    private final Map<Key, MetadataContributor> contributors;
    private final Elements elementUtils;
    private final Types typeUtils;

    public MetadataContributorRegistry(final ProcessingEnvironment processingEnvironment) {
        elementUtils = processingEnvironment.getElementUtils();
        typeUtils = processingEnvironment.getTypeUtils();
        contributors = ServiceLoader.load(MetadataContributor.class).stream()
                .map(it -> {
                    try {
                        return it.get();
                    } catch (final Error e) { // likely incremental compilation (just tolerate it even in degraded mode)
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(toMap(this::toKey, identity()));
    }

    public void register(final MetadataContributor contributor) {
        this.contributors.put(toKey(contributor), contributor);
    }

    public Map<String, String> compute(final Element element) {
        if (contributors.isEmpty()) {
            return Map.of();
        }

        Map<String, String> values = null;
        for (final var it : element.getAnnotationMirrors()) {
            if (!(it.getAnnotationType().asElement() instanceof TypeElement te)) {
                continue;
            }

            final var conf = contributors.get(new Key(
                    typeUtils, te.getQualifiedName().toString().hashCode(), it.getAnnotationType()));
            if (conf == null) {
                continue;
            }

            if (values == null) {
                values = new HashMap<>(2);
            }
            final var annotValues = elementUtils.getElementValuesWithDefaults(it).entrySet().stream()
                    .collect(toMap(m -> m.getKey().getSimpleName().toString(), Map.Entry::getValue));
            values.put(
                    valueOr(annotValues.get("name"), conf::name),
                    valueOr(annotValues.get("value"), conf::value));
        }
        return values == null ? Map.of() : values;
    }

    private String valueOr(final AnnotationValue value, final Supplier<String> fallback) {
        return value == null ? fallback.get() : value.toString();
    }

    private Key toKey(final MetadataContributor it) {
        return new Key(typeUtils, it.annotationType().hashCode(), typeUtils.getDeclaredType(elementUtils.getTypeElement(it.annotationType())));
    }

    private record Key(Types types, int hash, DeclaredType type) {
        @Override
        public boolean equals(final Object obj) {
            return obj instanceof Key k && types.isSameType(type, k.type());
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
