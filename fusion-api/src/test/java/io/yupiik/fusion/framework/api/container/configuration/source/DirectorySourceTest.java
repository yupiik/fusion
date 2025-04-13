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
package io.yupiik.fusion.framework.api.container.configuration.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DirectorySourceTest {
    @Test
    void emptyPrefix(@TempDir final Path work) throws IOException {
        Files.writeString(work.resolve("test"), "some");
        Files.writeString(work.resolve("service.account"), "content");
        final var source = new DirectorySource("", work);
        assertEquals("some", source.get("test"));
        assertEquals("content", source.get("service.account"));
        assertNull(source.get("missing"));
    }

    @Test
    void withPrefix(@TempDir final Path work) throws IOException {
        Files.writeString(work.resolve("test"), "some");
        Files.writeString(work.resolve("service.account"), "content");
        final var source = new DirectorySource("app.", work);
        assertEquals("some", source.get("app.test"));
        assertEquals("content", source.get("app.service.account"));
        assertNull(source.get("missing"));
        assertNull(source.get("app.missing"));
    }
}
