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
package io.yupiik.fusion.framework.handlebars.compiler.accessor;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapAccessorTest {
    @Test
    void simple() {
        assertEquals("junit", new MapAccessor().find(Map.of("test", "junit"), "test"));
    }

    @Test
    void dotted() {
        assertEquals("junit", new MapAccessor()
                .find(Map.of("test", Map.of("framework", "junit")), "test.framework"));
    }

    @Test
    void list() {
        assertEquals("junit", new MapAccessor()
                .find(Map.of("test", List.of("junit")), "test.0"));
    }

    @Test
    void complex() {
        assertEquals("1234", new MapAccessor()
                .find(
                        Map.of(
                                "l1",
                                Map.of("l2", Map.of(
                                        "l3", List.of(
                                                Map.of(
                                                        "l4", BigDecimal.valueOf(1234)))))),
                        "l1.l2.l3.0.l4")
                .toString());
    }
}
