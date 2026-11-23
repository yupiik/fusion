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
package io.yupiik.fusion.framework.api.configuration.internal;

import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.configuration.MissingRequiredParameterException;

import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

/** Shared mechanics used by generated configuration constructors. Not an application API. */
public final class ConfigurationValues {
    public static final Function<String, String> STRING = Function.identity();
    public static final Function<String, CharSequence> CHAR_SEQUENCE = value -> value;
    public static final Function<String, Boolean> BOOLEAN = Boolean::parseBoolean;
    public static final Function<String, Integer> INTEGER = Integer::parseInt;
    public static final Function<String, Long> LONG = Long::parseLong;
    public static final Function<String, Float> FLOAT = Float::parseFloat;
    public static final Function<String, Double> DOUBLE = Double::parseDouble;
    public static final Function<String, BigInteger> BIG_INTEGER = BigInteger::new;
    public static final Function<String, BigDecimal> BIG_DECIMAL = BigDecimal::new;

    private ConfigurationValues() {
        // no-op
    }

    public static Optional<String> raw(final Configuration configuration, final String key,
                                       final Function<String, String> keyMapper, final boolean required) {
        final var value = configuration.get(key);
        if (required && value.isEmpty()) {
            throw new MissingRequiredParameterException("No value for '" + keyMapper.apply(key) + "'");
        }
        return value;
    }

    public static <T> Optional<T> scalar(final Configuration configuration, final String key,
                                          final Function<String, String> keyMapper, final boolean required,
                                          final Function<String, T> conversion) {
        return raw(configuration, key, keyMapper, required).map(conversion);
    }

    public static <T> Optional<List<T>> list(final Configuration configuration, final String key,
                                             final Function<String, String> keyMapper, final boolean required,
                                             final Function<String, T> conversion) {
        final var raw = raw(configuration, key, keyMapper, required);
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        final var items = new ArrayList<T>();
        for (final var item : split(raw.get())) {
            final var stripped = item.strip();
            if (!stripped.isBlank()) {
                items.add(conversion.apply(stripped));
            }
        }
        return Optional.of(items.stream().toList());
    }

    public static <T> Optional<Map<String, T>> map(final Configuration configuration, final String key,
                                                   final Function<String, String> keyMapper, final boolean required,
                                                   final Function<String, T> conversion) {
        final var raw = raw(configuration, key, keyMapper, required);
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        final var properties = properties(raw.get());
        final var items = new HashMap<String, T>();
        for (final var name : properties.stringPropertyNames()) {
            items.put(name, java.util.Objects.requireNonNull(conversion.apply(properties.getProperty(name))));
        }
        return Optional.of(items);
    }

    public static String[] split(final String value) {
        return value.startsWith("sep=") ? value.substring(6).split(value.substring(4, 5)) : value.split(",");
    }

    public static Properties properties(final String value) {
        final var properties = new Properties();
        try (final var reader = new StringReader(value)) {
            properties.load(reader);
        } catch (final Exception e) {
            // Preserve partial properties and the historical tolerance of malformed input.
        }
        return properties;
    }

    public static int length(final Configuration configuration, final String key) {
        final var size = configuration.get(key + ".length");
        return size.map(Integer::parseInt).orElse(-1);
    }

}
