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
package io.yupiik.fusion.framework.api.container;

import io.yupiik.fusion.framework.api.spi.FusionContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

public class Contexts implements AutoCloseable {
    private final Map<Class<?>, Optional<FusionContext>> contexts = new HashMap<>();

    public Map<Class<?>, Optional<FusionContext>> getContexts() {
        return contexts;
    }

    public Optional<FusionContext> findContext(final Class<?> marker) {
        final var fusionContext = contexts.get(marker);
        if (fusionContext == null) {
            return Optional.empty();
        }
        return fusionContext;
    }

    public void doRegister(final FusionContext... contexts) {
        this.contexts.putAll(Stream.of(contexts).collect(toMap(FusionContext::marker, Optional::of)));
    }

    @Override
    public void close() {
        final var error = new IllegalStateException("Can't close the contexts properly");
        contexts.values().stream()
                .map(Optional::orElseThrow)
                .filter(AutoCloseable.class::isInstance)
                .map(AutoCloseable.class::cast)
                .sorted(comparing(it -> it.getClass().getName())) // be deterministic
                .forEach(c -> {
                    try {
                        c.close();
                    } catch (final Exception e) {
                        error.addSuppressed(e);
                    }
                });
        if (error.getSuppressed().length > 0) {
            throw error;
        }
    }
}
