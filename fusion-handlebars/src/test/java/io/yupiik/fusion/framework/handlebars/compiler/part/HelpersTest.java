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
package io.yupiik.fusion.framework.handlebars.compiler.part;

import io.yupiik.fusion.framework.handlebars.compiler.accessor.MapAccessor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HelpersTest {
    @Test
    void singleArg() {
        final var eval = Helpers.parseArgs("test");
        assertEquals(1, eval.size());
        assertEquals("junit", eval.get(0).eval(new MapAccessor(), Map.of("test", "junit")));
    }
    @Test
    void oneDataOneNumber() {
        final var eval = Helpers.parseArgs("test 3");
        assertEquals(2, eval.size());

        final var accessor = new MapAccessor();
        final var data = Map.of("test", "junit");
        assertEquals("junit", eval.get(0).eval(accessor, data));
        assertEquals(3, eval.get(1).eval(accessor, data));
    }

    @Test
    void oneStringOneNumber() {
        final var eval = Helpers.parseArgs("\"test\" 3");
        assertEquals(2, eval.size());

        final var accessor = new MapAccessor();
        final var data = Map.of("test", "junit");
        assertEquals("test", eval.get(0).eval(accessor, data));
        assertEquals(3, eval.get(1).eval(accessor, data));
    }
}
