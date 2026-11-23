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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationValuesTest {
    @Test
    void scalarConversions() {
        assertEquals("raw", ConfigurationValues.STRING.apply("raw"));
        assertEquals("raw", ConfigurationValues.CHAR_SEQUENCE.apply("raw"));
        assertEquals(true, ConfigurationValues.BOOLEAN.apply("TRUE"));
        assertEquals(false, ConfigurationValues.BOOLEAN.apply("anything"));
        assertEquals(12, ConfigurationValues.INTEGER.apply("12"));
        assertEquals(12000000000L, ConfigurationValues.LONG.apply("12000000000"));
        assertEquals(1.25f, ConfigurationValues.FLOAT.apply("1.25"));
        assertEquals(2.5, ConfigurationValues.DOUBLE.apply("2.5"));
        assertEquals(new BigInteger("12345678901234567890"), ConfigurationValues.BIG_INTEGER.apply("12345678901234567890"));
        assertEquals(new BigDecimal("2.30"), ConfigurationValues.BIG_DECIMAL.apply("2.30"));
    }

    @Test
    void listsKeepSeparatorRegexStrippingAndUnmodifiableResults() {
        final Configuration configuration = key -> Optional.of("sep=; 1 ; ; 2 ;");
        final var result = ConfigurationValues.list(configuration, "list", Function.identity(), false, ConfigurationValues.INTEGER).orElseThrow();
        assertEquals(List.of(1, 2), result);
        assertThrows(UnsupportedOperationException.class, () -> result.add(3));
        assertEquals(List.of("a", "b"), ConfigurationValues.list(key -> Optional.of("a, ,b,"), "list", Function.identity(), true, ConfigurationValues.STRING).orElseThrow());
        assertEquals(2, ConfigurationValues.split("sep=| ab").length); // separator remains a regex
        assertThrows(StringIndexOutOfBoundsException.class, () -> ConfigurationValues.split("sep="));
    }

    @Test
    void propertiesKeepEscapingPartialParsingAndMutableResults() {
        final Configuration configuration = key -> Optional.of("one=1\ntwo:2\n");
        final var result = ConfigurationValues.map(configuration, "map", Function.identity(), false, ConfigurationValues.INTEGER).orElseThrow();
        assertEquals(Map.of("one", 1, "two", 2), result);
        result.put("three", 3);
        assertEquals(3, result.get("three"));
        assertEquals("hello world", ConfigurationValues.properties("a=hello\\ world").getProperty("a"));
        assertEquals("kept", ConfigurationValues.properties("a=kept\nb=\\uXXXX").getProperty("a"));
        assertThrows(NumberFormatException.class, () -> ConfigurationValues.map(key -> Optional.of("a=bad"), "map", Function.identity(), false, ConfigurationValues.INTEGER));
    }

    @Test
    void nullableConversionsAndFailuresRetainOptionalAndCollectionBehavior() {
        final Configuration present = key -> Optional.of("value");
        final Function<String, String> nullable = value -> null;
        final Function<String, String> unusedMapper = key -> { throw new AssertionError("Unexpected key mapping"); };
        assertTrue(ConfigurationValues.scalar(present, "scalar", unusedMapper, true, nullable).isEmpty());
        final var list = ConfigurationValues.list(present, "list", unusedMapper, true, nullable).orElseThrow();
        assertEquals(1, list.size());
        assertEquals(null, list.get(0));
        assertThrows(UnsupportedOperationException.class, list::clear);
        assertThrows(NullPointerException.class,
                () -> ConfigurationValues.map(present, "map", unusedMapper, true, nullable));
        final var failure = new IllegalStateException("lookup failure");
        final Configuration broken = key -> { throw failure; };
        assertEquals(failure, assertThrows(IllegalStateException.class,
                () -> ConfigurationValues.scalar(broken, "scalar", unusedMapper, false, ConfigurationValues.INTEGER)));
        assertEquals("No value for 'mapped-list'", assertThrows(MissingRequiredParameterException.class,
                () -> ConfigurationValues.list(key -> Optional.empty(), "list", key -> "mapped-" + key, true, nullable)).getMessage());
        assertEquals("No value for 'mapped-map'", assertThrows(MissingRequiredParameterException.class,
                () -> ConfigurationValues.map(key -> Optional.empty(), "map", key -> "mapped-" + key, true, nullable)).getMessage());
    }

    @Test
    void missingRequiredDiagnosticsAndIndexedLengths() {
        final Configuration missing = key -> Optional.empty();
        final Function<String, String> mapper = key -> "mapped-" + key;
        assertTrue(ConfigurationValues.scalar(missing, "key", mapper, false, ConfigurationValues.STRING).isEmpty());
        assertTrue(ConfigurationValues.list(missing, "key", mapper, false, ConfigurationValues.STRING).isEmpty());
        assertTrue(ConfigurationValues.map(missing, "key", mapper, false, ConfigurationValues.STRING).isEmpty());
        final var error = assertThrows(MissingRequiredParameterException.class,
                () -> ConfigurationValues.scalar(missing, "key", mapper, true, ConfigurationValues.INTEGER));
        assertEquals("No value for 'mapped-key'", error.getMessage());
        assertEquals(-1, ConfigurationValues.length(missing, "items"));
        final var values = Map.of("items.length", "0", "negative.length", "-2", "invalid.length", "bad");
        final Configuration configuration = key -> Optional.ofNullable(values.get(key));
        assertEquals(0, ConfigurationValues.length(configuration, "items"));
        assertEquals(-2, ConfigurationValues.length(configuration, "negative"));
        assertThrows(NumberFormatException.class, () -> ConfigurationValues.length(configuration, "invalid"));
    }
}
