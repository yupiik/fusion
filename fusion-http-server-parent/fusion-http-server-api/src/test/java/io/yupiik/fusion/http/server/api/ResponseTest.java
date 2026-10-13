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
package io.yupiik.fusion.http.server.api;

import io.yupiik.fusion.http.server.impl.FusionResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseTest {
    @Test
    void headerInt() {
        assertHeader((b, k) -> b.header(k, 1), List.of("1"));
    }

    @Test
    void headerLong() {
        assertHeader((b, k) -> b.header(k, 1L), List.of("1"));
    }

    @Test
    void headerBoolean() {
        assertHeader((b, k) -> b.header(k, true), List.of("true"));
    }

    @Test
    void headerDate() {
        assertHeader(
                (b, k) -> b.header(k, ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC"))),
                List.of("Thu, 01 Jan 1970 00:00:00 UTC"));
    }

    private void assertHeader(final BiConsumer<Response.Builder, String> act, final List<String> expected) {
        final var builder = new FusionResponse.Builder();
        act.accept(builder, "test");
        assertEquals(expected, builder.build().headers().get("test"));
    }
}
