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
import java.util.Properties;
import java.util.function.Function;

import static java.util.Optional.empty;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;

// do not use JsonMapper injection since this one will depend on the configuration
public class JsonConfigurationSource extends MapConfigSource {
    private final Function<String, String> keyNormalizer;

    public JsonConfigurationSource(final ReaderSupplier supplier, final Function<String, String> keyNormalizer) {
        super(flatten(supplier, true, keyNormalizer));
        this.keyNormalizer = keyNormalizer;
    }

    public JsonConfigurationSource(final Reader reader, final Function<String, String> keyNormalizer) {
        super(flatten(() -> reader, false, keyNormalizer));
        this.keyNormalizer = keyNormalizer;
    }

    public JsonConfigurationSource(final ReaderSupplier supplier) {
        this(supplier, identity());
    }

    public JsonConfigurationSource(final Reader reader) {
        this(reader, identity());
    }

    @Override
    public String get(final String key) {
        return super.get(keyNormalizer.apply(key));
    }

    private static Map<String, String> flatten(final ReaderSupplier supplier, final boolean close,
                                               final Function<String, String> keyNormalizer) {
        try (final var mapper = new JsonMapperImpl(List.of(), key -> empty())) {
            try(final var reader = close ? new BufferedReader(supplier.get()) : new BufferedReader(supplier.get()) {
                @Override
                public void close() {
                    // no-op
                }
            }) {
                final var raw = mapper.fromString(Object.class, reader.lines().collect(joining("\n")));
                final var result = new LinkedHashMap<String, String>();
                doFlatten("", raw, result, keyNormalizer);
                return result;
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String key(final String prefix, final String suffix,
                              final Function<String, String> keyNormalizer) {
        return keyNormalizer.apply(prefix.isEmpty() ? suffix : prefix + "." + suffix);
    }

    private static void doFlatten(final String prefix, final Object value, final Map<String, String> result,
                                  final Function<String, String> keyNormalizer) {
        if (value == null) {
            return;
        }

        if (isPrimitive(value)) {
            result.put(prefix, value.toString());
            return;
        }

        if (value instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                result.put(key(prefix, "length", keyNormalizer), "0");
                return;
            }

            final var allPrimitives = collection.stream().allMatch(JsonConfigurationSource::isPrimitive);
            if (allPrimitives) {
                result.put(prefix, collection.stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .collect(joining(", ")));
            } else {
                result.put(key(prefix, "length", keyNormalizer), Integer.toString(collection.size()));
                var index = 0;
                for (final var item : collection) {
                    doFlatten(key(prefix, Integer.toString(index), keyNormalizer), item, result, keyNormalizer);
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
                    result.put(key(prefix, "length", keyNormalizer), Integer.toString(entries.size()));
                    var index = 0;
                    for (final var entry : entries.entrySet()) {
                        doFlatten(key(prefix, index + ".key", keyNormalizer), entry.getKey(), result, keyNormalizer);
                        doFlatten(key(prefix, index + ".value", keyNormalizer), entry.getValue(), result, keyNormalizer);
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
                        result.put(key(prefix, entryKey, keyNormalizer), entryValue.toString());
                    } else {
                        doFlatten(key(prefix, entryKey, keyNormalizer), entryValue, result, keyNormalizer);
                    }
                }
            }
        }
    }

    private static boolean isPrimitive(final Object v) {
        return v == null || v instanceof Boolean || v instanceof String || v instanceof Number;
    }
}
