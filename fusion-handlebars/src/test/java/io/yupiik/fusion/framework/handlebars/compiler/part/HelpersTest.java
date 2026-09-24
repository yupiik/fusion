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
import io.yupiik.fusion.framework.handlebars.helper.HelperContext;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HelpersTest {
    private final Helpers helpers = new Helpers(Map.of(
            "eq", (Function<HelperContext, Object>) (ctx) -> null,
            "echo", (Function<HelperContext, Object>) (ctx) -> ctx.args().isEmpty() ? null : ctx.args().get(0)));

    @Test
    void singleArg() {
        final var parsed = helpers.parseArgs("test");
        assertEquals(1, parsed.args().size());
        assertEquals("junit", parsed.args().get(0).eval(new MapAccessor(), Map.of("test", "junit")));
    }

    @Test
    void oneDataOneNumber() {
        final var parsed = helpers.parseArgs("test 3");
        assertEquals(2, parsed.args().size());
        assertEquals("junit", parsed.args().get(0).eval(new MapAccessor(), Map.of("test", "junit")));
        assertEquals(3, parsed.args().get(1).eval(new MapAccessor(), Map.of("test", "junit")));
    }

    @Test
    void oneStringOneNumber() {
        final var parsed = helpers.parseArgs("\"test\" 3");
        assertEquals(2, parsed.args().size());
        assertEquals("test", parsed.args().get(0).eval(new MapAccessor(), Map.of()));
        assertEquals(3, parsed.args().get(1).eval(new MapAccessor(), Map.of()));
    }

    @Test
    void quotedWithSpacesAndEquals() {
        final var parsed = helpers.parseArgs("a=\"x y=z\"");
        assertEquals(0, parsed.args().size());
        assertEquals("x y=z", parsed.hash().get("a").eval(new MapAccessor(), Map.of()));
    }

    @Test
    void hashArgs() {
        final var parsed = helpers.parseArgs("a=1 b=\"x\" c=name");
        assertEquals(0, parsed.args().size());
        assertEquals(1, parsed.hash().get("a").eval(new MapAccessor(), Map.of()));
        assertEquals("x", parsed.hash().get("b").eval(new MapAccessor(), Map.of()));
        assertEquals("junit", parsed.hash().get("c").eval(new MapAccessor(), Map.of("name", "junit")));
    }

    @Test
    void mixedArgsAndHash() {
        final var parsed = helpers.parseArgs("test 3 sep=\"-\"");
        assertEquals(2, parsed.args().size());
        assertEquals(1, parsed.hash().size());
        assertEquals("-", parsed.hash().get("sep").eval(new MapAccessor(), Map.of()));
    }

    @Test
    void subExpression() {
        final var parsed = helpers.parseArgs("(eq a b)");
        assertEquals(1, parsed.args().size());
        final var eval = parsed.args().get(0);
        assertEquals(null, eval.eval(new MapAccessor(), Map.of("a", "x", "b", "x")));
    }

    @Test
    void nestedSubExpression() {
        final var parsed = helpers.parseArgs("(eq (echo a) b)");
        assertEquals(1, parsed.args().size());
        final var eval = parsed.args().get(0);
        assertEquals(null, eval.eval(new MapAccessor(), Map.of("a", "x", "b", "x")));
    }

    @Test
    void subExpressionWithHash() {
        final var parsed = helpers.parseArgs("(echo a style=\"x\")");
        assertEquals(1, parsed.args().size());
    }

    @Test
    void unknownSubExpressionHelper() {
        assertThrows(IllegalArgumentException.class, () -> helpers.parseArgs("(unknown a)"));
    }

    @Test
    void unclosedParenthesis() {
        assertThrows(IllegalArgumentException.class, () -> helpers.parseArgs("(eq a b"));
        assertThrows(IllegalArgumentException.class, () -> helpers.parseArgs("(eq a b))"));
    }

    @Test
    void unclosedQuote() {
        assertThrows(IllegalArgumentException.class, () -> helpers.parseArgs("\"abc"));
        assertThrows(IllegalArgumentException.class, () -> helpers.parseArgs("'abc"));
    }

    @Test
    void singleQuotedString() {
        final var parsed = helpers.parseArgs("'abc' 3");
        assertEquals(2, parsed.args().size());
        assertEquals("abc", parsed.args().get(0).eval(new MapAccessor(), Map.of()));
        assertEquals(3, parsed.args().get(1).eval(new MapAccessor(), Map.of()));
    }

    @Test
    void singleQuotedHashValue() {
        final var parsed = helpers.parseArgs("fun='yes'");
        assertEquals(0, parsed.args().size());
        assertEquals("yes", parsed.hash().get("fun").eval(new MapAccessor(), Map.of()));
    }

    @Test
    void singleQuotedWithSpaces() {
        final var parsed = helpers.parseArgs("a='x y'");
        assertEquals(0, parsed.args().size());
        assertEquals("x y", parsed.hash().get("a").eval(new MapAccessor(), Map.of()));
    }

    @Test
    void singleQuotedEscaped() {
        final var parsed = helpers.parseArgs("'it\\'s'");
        assertEquals(1, parsed.args().size());
        assertEquals("it's", parsed.args().get(0).eval(new MapAccessor(), Map.of()));
    }

    @Test
    void doubleQuotedWithApostrophe() {
        final var parsed = helpers.parseArgs("\"Alan's world\"");
        assertEquals(1, parsed.args().size());
        assertEquals("Alan's world", parsed.args().get(0).eval(new MapAccessor(), Map.of()));
    }

    @Test
    void quoteInMiddleOfArgument() {
        assertThrows(IllegalArgumentException.class, () -> helpers.parseArgs("wo\"rld\""));
        assertThrows(IllegalArgumentException.class, () -> helpers.parseArgs("wo'rld'"));
    }
}