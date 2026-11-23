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
import io.yupiik.fusion.framework.processor.internal.generator.MetadataContributorGenerator;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toMap;

public class MetadataContributorRegistry {
    private final Map<String, MetadataContributor> contributors = new HashMap<>();
    private final Elements elementUtils;

    public MetadataContributorRegistry(final ProcessingEnvironment processingEnvironment) {
        elementUtils = processingEnvironment.getElementUtils();
        final var contextLoader = Thread.currentThread().getContextClassLoader();
        final var loader = contextLoader == null ? MetadataContributorRegistry.class.getClassLoader() : contextLoader;
        // Handwritten providers override dependency annotation metadata in provider-name order.
        // Explicit current-compilation registrations win last.
        final var providers = new ArrayList<MetadataContributor>();
        final var iterator = ServiceLoader.load(MetadataContributor.class, loader).iterator();
        while (true) {
            try {
                if (!iterator.hasNext()) {
                    break;
                }
                providers.add(iterator.next());
            } catch (final ServiceConfigurationError | LinkageError e) {
                // Stale incremental service entries can fail during discovery, before instantiation.
            }
        }
        providers.stream()
                .sorted(Comparator.comparing(it -> it.getClass().getName()))
                .forEach(this::register);
    }

    public void register(final MetadataContributor contributor) {
        // Legacy handwritten providers can use binary names for nested annotations.
        final var declared = elementUtils.getTypeElement(contributor.annotationType());
        final var type = declared == null ? elementUtils.getTypeElement(contributor.annotationType().replace('$', '.')) : declared;
        contributors.put(type == null ? contributor.annotationType() : type.getQualifiedName().toString(), contributor);
    }

    public Map<String, String> compute(final Element element) {
        Map<String, String> values = null;
        for (final var it : element.getAnnotationMirrors()) {
            if (!(it.getAnnotationType().asElement() instanceof TypeElement te)) {
                continue;
            }
            final var registered = contributors.get(te.getQualifiedName().toString());
            // Use javac's actual source/classpath selection, independent of the processor loader.
            final var conf = registered == null ? new MetadataContributorGenerator(elementUtils, te).get() : registered;
            if (conf == null) {
                continue;
            }
            if (values == null) {
                values = new HashMap<>(2);
            }
            final var annotValues = elementUtils.getElementValuesWithDefaults(it).entrySet().stream()
                    .collect(toMap(m -> m.getKey().getSimpleName().toString(), Map.Entry::getValue));
            values.put(valueOr(annotValues.get("name"), conf::name), valueOr(annotValues.get("value"), conf::value));
        }
        return values == null ? Map.of() : values;
    }

    private String valueOr(final AnnotationValue value, final Supplier<String> fallback) {
        return value == null ? fallback.get() : value.toString();
    }
}
