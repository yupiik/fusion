/*
 * Copyright (c) 2022-2023 - Yupiik SAS - https://www.yupiik.com
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
package io.yupiik.fusion.json.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonStringsTest {
    @Test
    void noEscape() {
        assertEquals("\"ok\"", escape("ok"));
    }

    @Test
    void escapes() {
        assertEquals("\"\\t\"", escape("\t"));
        assertEquals("\"\\n\"", escape("\n"));
        assertEquals("\"9\"", escape("\u0039"));
        assertEquals("\"\\t\"", escape("\u0009"));
        assertEquals("\"Ὡ\"", escape("\u1F69"));
        assertEquals("\"®\"", escape("\u00AE"));
    }

    @Test
    void overflowDueToEscaping() {
        assertEquals("\"\\t\\t\\t\\t\\t\\t\\t\\t\\t\\t\\t\\t\\t\\t\\t\\t\"", escape("\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t"));
    }

    private String escape(final String value) {
        final var chars = JsonStrings.escapeChars(value);
        return new String(chars.array(), 0, chars.limit());
    }
}
