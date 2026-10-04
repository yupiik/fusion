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
package io.yupiik.fusion.framework.api.main;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArgsConfigSourceTest {
    @Test
    void separatedBySpace() {
        final var empty = new ArgsConfigSource(List.of());
        assertNull(empty.get("test"));
        assertNull(empty.get("-test"));
        assertNull(empty.get("--test"));

        assertEquals("bar", new ArgsConfigSource(List.of("-test", "bar")).get("test"));
        assertEquals("bar", new ArgsConfigSource(List.of("--test", "bar")).get("test"));
    }

    @Test
    void separatedByEquals() {
        assertEquals("bar", new ArgsConfigSource(List.of("-test=bar")).get("test"));
        assertEquals("bar", new ArgsConfigSource(List.of("--test=bar")).get("test"));
    }

    @Test
    void bareTokensAreIgnored() {
        final var source = new ArgsConfigSource(List.of("client", "list", "--url", "x", "trailing"));
        assertNull(source.get("client"));
        assertNull(source.get("list"));
        assertNull(source.get("trailing"));
        assertEquals("x", source.get("url"));
    }

    @Test
    void mixed() {
        assertEquals("bar2", new ArgsConfigSource(List.of("--test=bar1", "--foo", "bar2")).get("foo"));
        assertEquals("bar2", new ArgsConfigSource(List.of("--test", "bar1", "--foo=bar2")).get("foo"));
    }

    @Test
    void optionFollowedByOptionRequiresValue() {
        assertEquals("Option '--foo' requires a value (use '--foo=value')",
                assertThrows(IllegalArgumentException.class,
                        () -> new ArgsConfigSource(List.of("--foo", "--bar"))).getMessage());
    }

    @Test
    void trailingOptionRequiresValue() {
        assertEquals("Option '--foo' requires a value (use '--foo=value')",
                assertThrows(IllegalArgumentException.class,
                        () -> new ArgsConfigSource(List.of("--foo"))).getMessage());
    }

    @Test
    void helpTokenIsSkipped() {
        final var source = new ArgsConfigSource(List.of("my-app", "--help", "--foo", "bar"));
        assertNull(source.get("help"));
        assertEquals("bar", source.get("foo")); // the rest still binds normally
    }

    @Test
    void negativeNumberIsAValue() {
        final var source = new ArgsConfigSource(List.of("--max", "-1", "--ratio", "-0.5"));
        assertEquals("-1", source.get("max"));
        assertEquals("-0.5", source.get("ratio"));
    }

    @Test
    void dotTolerance() {
        assertEquals("1", new ArgsConfigSource(List.of("--foo-bar", "1")).get("foo.bar"));
        assertEquals("1", new ArgsConfigSource(List.of("--foo.bar", "1")).get("foo-bar"));
        assertEquals("1", new ArgsConfigSource(List.of("--foo.bar", "1")).get("foo.bar"));
    }

    @Test
    void noPrefixRootConfiguration() { // old generated factories ("-" root configurations) read `-.x` keys
        assertEquals("8080", new ArgsConfigSource(List.of("--port", "8080")).get("-.port"));
        assertEquals("1", new ArgsConfigSource(List.of("--foo-bar", "1")).get("-.foo.bar"));
    }

    @Test
    void negatedFlagKeepsNoKeyAndDefaultsPositive() {
        final var source = new ArgsConfigSource(List.of("--no-foo"));
        assertEquals("false", source.get("no-foo"));
        assertEquals("false", source.get("no.foo"));
        assertEquals("false", source.get("foo")); // negation is exposed as the positive default
        assertEquals("false", source.get("-.foo")); // legacy stripped lookup resolves the negation too
    }

    @Test
    void negatedMultiSegment() {
        final var source = new ArgsConfigSource(List.of("--no-foo-bar"));
        assertEquals("false", source.get("foo.bar"));
        assertEquals("false", source.get("foo-bar"));
    }

    @Test
    void negatedWithExplicitValue() {
        final var source = new ArgsConfigSource(List.of("--no-foo=bar"));
        assertEquals("bar", source.get("no-foo"));
        assertNull(source.get("foo")); // explicit value binds no-foo only, no positive default
    }

    @Test
    void negationDoesNotConsumeNextToken() {
        final var source = new ArgsConfigSource(List.of("--no-foo", "v"));
        assertEquals("false", source.get("foo"));
        assertNull(source.get("v")); // the following bare token is not the negation value
    }

    @Test
    void explicitEmptyEqualsValue() {
        assertEquals("", new ArgsConfigSource(List.of("--foo=")).get("foo"));
    }

    @Test
    void repeatedValuesJoin() {
        final var source = new ArgsConfigSource(List.of("--foo", "bar1", "--foo", "bar2"));
        assertEquals("bar1,bar2", source.get("foo"));
    }

    @Test
    void dotAndDashAliasesWorkOnNegatedAndPositive() {
        final var source = new ArgsConfigSource(List.of("--no.deep-link"));
        assertEquals("false", source.get("deep-link"));
        assertEquals("false", source.get("deep.link"));
        assertEquals("false", source.get("no-deep-link"));
        assertEquals("false", source.get("no.deep.link"));
    }

    @Test
    void propertiesInline() {
        assertEquals("bar2", new ArgsConfigSource(List.of("--fusion-properties-whatever=foo=bar2")).get("foo"));
    }

    @Test
    void propertiesFile(@TempDir final Path work) throws IOException {
        final var location = Files.writeString(work.resolve("props.properties"), "foo=bar2");
        assertEquals("bar2", new ArgsConfigSource(List.of("--fusion-properties-whatever=" + location)).get("foo"));
    }
}