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
package io.yupiik.fusion.json.configuration;

import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.configuration.impl.MapConfigSource;
import io.yupiik.fusion.framework.api.io.ReaderSupplier;
import io.yupiik.fusion.json.internal.JsonMapperImpl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import static java.util.Optional.empty;
import static java.util.stream.Collectors.joining;

// do not use JsonMapper injection since this one will depend on the configuration
public class JsonConfigurationSource extends MapConfigSource {
    public JsonConfigurationSource(final ReaderSupplier supplier) {
        super(flatten(supplier, true));
    }

    public JsonConfigurationSource(final Reader reader) {
        super(flatten(() -> reader, false));
    }

    private static Map<String, String> flatten(final ReaderSupplier supplier, final boolean close) {
        try (final var mapper = new JsonMapperImpl(List.of(), new Configuration() {
            @Override
            public Optional<String> get(String key) {
                return switch (key) {
                    default -> empty();
                };
            }
        })) {
            final var reader = new BufferedReader(supplier.get());
            try {
                final var raw = mapper.fromString(Object.class, reader.lines().collect(joining("\n")));
                final var result = new LinkedHashMap<String, String>();
                doFlatten("", raw, result);
                return result;
            } finally {
                if (close) {
                    try {
                        reader.close();
                    } catch (final RuntimeException e) {
                        throw e;
                    } catch (final Exception e) {
                        // no-op
                    }
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String key(final String prefix, final String suffix) {
        return prefix.isEmpty() ? suffix : prefix + "." + suffix;
    }

    private static void doFlatten(final String prefix, final Object value, final Map<String, String> result) {
        if (value == null) {
            return;
        }

        if (isPrimitive(value)) {
            result.put(prefix, value.toString());
            return;
        }

        if (value instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                result.put(key(prefix, "length"), "0");
                return;
            }

            final var allPrimitives = collection.stream().allMatch(JsonConfigurationSource::isPrimitive);
            if (allPrimitives) {
                result.put(prefix, collection.stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .collect(joining(", ")));
            } else {
                result.put(key(prefix, "length"), Integer.toString(collection.size()));
                var index = 0;
                for (final var item : collection) {
                    doFlatten(key(prefix, Integer.toString(index)), item, result);
                    index++;
                }
            }
            return;
        }

        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked") final var stringMap = (Map<String, Object>) map;

            // this fake attribute enables to serialize in a format understood by the configurationfactories
            // for Map<String, Object> types else it will just look like nested objects
            final var asListRaw = stringMap.get("$asList");
            final var asList = asListRaw instanceof Boolean b && b;

            if (asList) {
                final var entries = new LinkedHashMap<>(stringMap);
                entries.remove("$asList");

                final var allPrimitives = entries.values().stream()
                        .allMatch(v -> v == null || v instanceof Boolean || v instanceof String || v instanceof Number);
                if (allPrimitives) {
                    final var props = new Properties();
                    for (final var e : entries.entrySet()) {
                        final var v = e.getValue();
                        if (v != null) {
                            props.setProperty(e.getKey(), v.toString());
                        }
                    }
                    try (final var sw = new StringWriter()) {
                        props.store(sw, null);
                        result.put(prefix, sw.toString());
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                } else {
                    result.put(key(prefix, "length"), Integer.toString(entries.size()));
                    var index = 0;
                    for (final var entry : entries.entrySet()) {
                        doFlatten(key(prefix, index + ".key"), entry.getKey(), result);
                        doFlatten(key(prefix, index + ".value"), entry.getValue(), result);
                        index++;
                    }
                }
            } else {
                for (final var entry : stringMap.entrySet()) {
                    final var entryKey = entry.getKey();
                    final var entryValue = entry.getValue();
                    if (entryValue == null) {
                        continue;
                    }
                    if (isPrimitive(entryValue)) {
                        result.put(key(prefix, entryKey), entryValue.toString());
                    } else {
                        doFlatten(key(prefix, entryKey), entryValue, result);
                    }
                }
            }
        }
    }

    private static boolean isPrimitive(final Object v) {
        return v == null || v instanceof Boolean || v instanceof String || v instanceof Number;
    }
}
