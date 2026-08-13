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
package io.yupiik.fusion.cli.internal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliCommandResolverTest {
    @Test
    void exactSingleSegment() {
        final var resolver = new CliCommandResolver(List.of(command("deploy")));
        final var resolution = resolver.resolve(List.of("deploy", "--x", "y"));
        assertTrue(resolution.isExact());
        assertFalse(resolution.isGroup());
        assertEquals(1, resolution.commandLen());
        assertEquals("deploy", resolution.command().name());
    }

    @Test
    void exactTwoSegments() {
        final var resolver = new CliCommandResolver(List.of(command("deploy", "run")));
        final var resolution = resolver.resolve(List.of("deploy", "run", "--x", "y"));
        assertTrue(resolution.isExact());
        assertEquals(2, resolution.commandLen());
        assertEquals("deploy/run", resolution.command().name());
    }

    @Test
    void exactThreeSegments() {
        final var resolver = new CliCommandResolver(List.of(command("a", "b", "c")));
        final var resolution = resolver.resolve(List.of("a", "b", "c"));
        assertTrue(resolution.isExact());
        assertEquals(3, resolution.commandLen());
        assertEquals("a/b/c", resolution.command().name());
    }

    @Test
    void exactFiveSegments() {
        final var resolver = new CliCommandResolver(List.of(command("a", "b", "c", "d", "e")));
        final var resolution = resolver.resolve(List.of("a", "b", "c", "d", "e"));
        assertTrue(resolution.isExact());
        assertEquals(5, resolution.commandLen());
        assertEquals("a/b/c/d/e", resolution.command().name());
    }

    @Test
    void longestExactMatchWins() {
        final var resolver = new CliCommandResolver(List.of(command("deploy"), command("deploy", "run")));
        final var resolution = resolver.resolve(List.of("deploy", "run", "--x", "y"));
        assertTrue(resolution.isExact());
        assertEquals(2, resolution.commandLen());
        assertEquals("deploy/run", resolution.command().name());
    }

    @Test
    void literalSlashIsASingleSegment() {
        final var resolver = new CliCommandResolver(List.of(command("deploy/run")));
        final var oneToken = resolver.resolve(List.of("deploy/run"));
        assertTrue(oneToken.isExact());
        assertEquals(1, oneToken.commandLen());
        assertArrayEquals(new String[]{"deploy/run"}, oneToken.command().path());

        final var twoTokens = resolver.resolve(List.of("deploy", "run"));
        assertFalse(twoTokens.isExact());
        assertFalse(twoTokens.isGroup());
    }

    @Test
    void literalSlashAndSplitSegmentsCoexist() {
        final var resolver = new CliCommandResolver(List.of(command("deploy/run"), command("deploy", "run")));
        assertTrue(resolver.resolve(List.of("deploy/run")).isExact());
        assertTrue(resolver.resolve(List.of("deploy", "run")).isExact());
        assertEquals(1, resolver.resolve(List.of("deploy/run")).commandLen());
        assertArrayEquals(new String[]{"deploy/run"}, resolver.resolve(List.of("deploy/run")).command().path());
        assertEquals(2, resolver.resolve(List.of("deploy", "run")).commandLen());
        assertArrayEquals(new String[]{"deploy", "run"}, resolver.resolve(List.of("deploy", "run")).command().path());
    }

    @Test
    void group() {
        final var resolver = new CliCommandResolver(List.of(command("deploy", "run"), command("deploy", "status")));
        final var resolution = resolver.resolve(List.of("deploy"));
        assertFalse(resolution.isExact());
        assertTrue(resolution.isGroup());
        assertEquals(List.of("deploy"), resolution.group());
        assertEquals(1, resolution.groupLen());
    }

    @Test
    void implicitGroup() {
        final var resolver = new CliCommandResolver(List.of(command("a", "b", "c", "d")));
        final var resolution = resolver.resolve(List.of("a", "b"));
        assertFalse(resolution.isExact());
        assertTrue(resolution.isGroup());
        assertEquals(List.of("a", "b"), resolution.group());
        assertEquals(2, resolution.groupLen());
    }

    @Test
    void implicitGroupInBetween() {
        final var resolver = new CliCommandResolver(List.of(command("a", "b", "c", "d")));
        final var resolution = resolver.resolve(List.of("a", "b", "x"));
        assertTrue(resolution.isGroup());
        assertEquals(List.of("a", "b"), resolution.group());
    }

    @Test
    void none() {
        final var resolver = new CliCommandResolver(List.of(command("deploy", "run")));
        final var resolution = resolver.resolve(List.of("unknown", "x"));
        assertFalse(resolution.isExact());
        assertFalse(resolution.isGroup());
    }

    @Test
    void duplicatePathFails() {
        assertThrows(IllegalArgumentException.class,
                () -> new CliCommandResolver(List.of(command("a", "b"), command("a", "b"))));
    }

    private static CliCommand<Runnable> command(final String... path) {
        return new BaseCliCommand<>(path, "desc of " + String.join("/", path), c -> null, (c, deps) -> () -> { }, List.of());
    }
}
