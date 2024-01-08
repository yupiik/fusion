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

class ArgsConfigSourceTest {
    @Test
    void separatedBySpace() {
        final var empty = new ArgsConfigSource(List.of());
        assertNull(empty.get("test"));
        assertNull(empty.get("-test"));
        assertNull(empty.get("--test"));

        assertEquals("bar", new ArgsConfigSource(List.of("test", "bar")).get("test"));
        assertEquals("bar", new ArgsConfigSource(List.of("-test", "bar")).get("test"));
        assertEquals("bar", new ArgsConfigSource(List.of("--test", "bar")).get("test"));
    }

    @Test
    void separatedByEquals() {
        assertEquals("bar", new ArgsConfigSource(List.of("test=bar")).get("test"));
        assertEquals("bar", new ArgsConfigSource(List.of("-test=bar")).get("test"));
        assertEquals("bar", new ArgsConfigSource(List.of("--test=bar")).get("test"));
    }

    @Test
    void mixed() {
        assertEquals("bar2", new ArgsConfigSource(List.of("--test=bar1", "--foo", "bar2")).get("foo"));
        assertEquals("bar2", new ArgsConfigSource(List.of("--test", "bar1", "--foo=bar2")).get("foo"));
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
