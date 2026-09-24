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
package io.yupiik.fusion.framework.handlebars;

import io.yupiik.fusion.framework.handlebars.compiler.accessor.MapAccessor;
import io.yupiik.fusion.framework.handlebars.helper.HelperContext;
import io.yupiik.fusion.framework.handlebars.spi.Template;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests ported from the handlebars.js spec suite (spec/builtins.js, spec/subexpressions.js, spec/helpers.js).
 * Cases deliberately not ported (documented deviations):
 * - context lambdas (functions as data values); this engine only resolves `Supplier`s.
 * - raw block helpers `{{{{raw}}}}...{{{{/raw}}}}` with a helper: our raw blocks are verbatim constants by design.
 * - `options.data`, `#log`, `helperMissing`, `knownHelpers`, helper registration (runtime-JS concepts).
 * - `options.lookupProperty`, `SafeString` is supported via {@link io.yupiik.fusion.framework.handlebars.helper.SafeString}.
 * The ported assertions use this engine's rendering: `each` joins items with `\n`, no trailing whitespace is added.
 */
class HandlebarsJsParityTest {
    // ---- built-ins (spec/builtins.js) ----

    @Test
    void ifBooleanStringFalseUndefined() {
        final var template = "{{#if goodbye}}GOODBYE {{/if}}cruel {{world}}!";
        assertEquals("GOODBYE cruel world!", render(template, Map.of("goodbye", true, "world", "world"), Map.of()));
        assertEquals("GOODBYE cruel world!", render(template, Map.of("goodbye", "dummy", "world", "world"), Map.of()));
        assertEquals("cruel world!", render(template, Map.of("goodbye", false, "world", "world"), Map.of()));
        assertEquals("cruel world!", render(template, Map.of("world", "world"), Map.of()));
    }

    @Test
    void ifArrayAndZeroTruthiness() {
        final var template = "{{#if goodbye}}GOODBYE {{/if}}cruel {{world}}!";
        assertEquals("GOODBYE cruel world!", render(template, Map.of("goodbye", List.of("foo"), "world", "world"), Map.of()));
        assertEquals("cruel world!", render(template, Map.of("goodbye", List.of(), "world", "world"), Map.of()));
        assertEquals("cruel world!", render(template, Map.of("goodbye", 0, "world", "world"), Map.of()));
    }

    @Test
    void withAndElse() {
        assertEquals("Alan Johnson", render("{{#with person}}{{first}} {{last}}{{/with}}",
                Map.of("person", Map.of("first", "Alan", "last", "Johnson")), Map.of()));
        assertEquals("Person is not present", render(
                "{{#with person}}Person is present{{else}}Person is not present{{/with}}",
                Map.of(), Map.of()));
    }

    @Test
    void eachWithDataVariables() {
        final var data = Map.of(
                "goodbyes", List.of(Map.of("text", "goodbye"), Map.of("text", "Goodbye"), Map.of("text", "GOODBYE")),
                "world", "world");
        // this engine joins each items with \n and strips the trailing whitespace of the block body (documented deviation)
        assertEquals("0. goodbye!\n1. Goodbye!\n2. GOODBYE!cruel world!",
                render("{{#each goodbyes}}{{@index}}. {{text}}! {{/each}}cruel {{world}}!", data, Map.of()));
        assertEquals("goodbye! cruel world!",
                render("{{#each goodbyes}}{{#if @first}}{{text}}! {{/if}}{{/each}}cruel {{world}}!", data, Map.of()));
        assertEquals("GOODBYE! cruel world!",
                render("{{#each goodbyes}}{{#if @last}}{{text}}! {{/if}}{{/each}}cruel {{world}}!", data, Map.of()));
    }

    @Test
    void eachOnObjectWithKeyValue() {
        final var goodbyes = new java.util.TreeMap<>(Map.of("a", Map.of("text", "goodbye"), "b", Map.of("text", "Goodbye")));
        final var data = Map.of("goodbyes", goodbyes, "world", "world");
        assertEquals("a. goodbye!\nb. Goodbye!cruel world!",
                render("{{#each goodbyes}}{{@key}}. {{text}}! {{/each}}cruel {{world}}!", data, Map.of()));
        assertEquals("goodbye! cruel world!",
                render("{{#each goodbyes}}{{#if @first}}{{text}}! {{/if}}{{/each}}cruel {{world}}!", data, Map.of()));
    }

    @Test
    void eachEmptyAndUndefined() {
        assertEquals("cruel world!",
                render("{{#each goodbyes}}{{text}}! {{/each}}cruel {{world}}!", Map.of("goodbyes", List.of(), "world", "world"), Map.of()));
        assertEquals("cruel world!",
                render("{{#each goodbyes}}{{text}}! {{/each}}cruel {{world}}!", Map.of("goodbyes", Map.of(), "world", "world"), Map.of()));
        assertEquals("cruel !",
                render("{{#each goodbyes}}{{text}}! {{/each}}cruel {{world}}!", null, Map.of()));
    }

    @Test
    void eachWithoutArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> compile("{{#each}}{{text}}! {{/each}}cruel world!"));
    }

    @Test
    void lookupWithParentFallback() {
        // `../` is not supported by this engine: the parent fallback of `each` resolves `data` directly.
        assertEquals("foo\nbar", render("{{#each goodbyes}}{{lookup data .}}{{/each}}",
                Map.of("goodbyes", List.of(0, 1), "data", List.of("foo", "bar")), Map.of()));
        assertEquals("", render("{{#each goodbyes}}{{lookup bar .}}{{/each}}",
                Map.of("goodbyes", List.of(0, 1), "data", List.of("foo", "bar")), Map.of()));
    }

    // ---- sub-expressions (spec/subexpressions.js) ----

    private Object foo(final HelperContext ctx) {
        return ctx.args().get(0).toString() + ctx.args().get(0).toString();
    }

    private Object bar(final HelperContext ctx) {
        return "LOL";
    }

    @Test
    void subExpressionArgLess() {
        assertEquals("LOLLOL!", render("{{foo (bar)}}!", Map.of(), helpers("foo", this::foo, "bar", this::bar)));
    }

    @Test
    void subExpressionWithArgs() {
        assertEquals("val is true", render("{{blog (equal a b)}}",
                Map.of("bar", "LOL"),
                helpers("blog", ctx -> "val is " + ctx.args().get(0),
                        "equal", ctx -> Objects.equals(ctx.args().get(0), ctx.args().get(1)))));
    }

    @Test
    void subExpressionMixedPathsAndHelpers() {
        assertEquals("val is foo!, true and bar!", render("{{blog baz.bat (equal a b) baz.bar}}",
                Map.of("bar", "LOL", "baz", Map.of("bat", "foo!", "bar", "bar!"), "a", "x", "b", "x"),
                helpers("blog", ctx -> "val is " + ctx.args().get(0) + ", " + ctx.args().get(1) + " and " + ctx.args().get(2),
                        "equal", ctx -> ctx.args().get(0).equals(ctx.args().get(1)))));
    }

    @Test
    void subExpressionMuchNesting() {
        assertEquals("val is true", render("{{blog (equal (equal true true) true)}}",
                Map.of(),
                helpers("blog", ctx -> "val is " + ctx.args().get(0),
                        "equal", ctx -> Objects.equals(ctx.args().get(0), ctx.args().get(1)))));
    }

    @Test
    void subExpressionComplexGh800() {
        final var context = Map.of("a", "a", "b", "b", "c", Map.of("c", "c"), "d", "d", "e", Map.of("e", "e"));
        final var helpers = helpers(
                "dash", ctx -> ctx.args().get(0) + "-" + ctx.args().get(1),
                "concat", ctx -> String.valueOf(ctx.args().get(0)) + ctx.args().get(1));
        assertEquals("abc-ab", render("{{dash 'abc' (concat a b)}}", context, helpers));
        assertEquals("d-ab", render("{{dash d (concat a b)}}", context, helpers));
        assertEquals("c-ab", render("{{dash c.c (concat a b)}}", context, helpers));
        assertEquals("ab-c", render("{{dash (concat a b) c.c}}", context, helpers));
        assertEquals("ae-c", render("{{dash (concat a e.e) c.c}}", context, helpers));
    }

    @Test
    void subExpressionAsHashes() {
        assertEquals("val is true", render("{{blog fun=(equal (blog fun=1) 'val is 1')}}",
                Map.of(),
                helpers("blog", ctx -> "val is " + ctx.hash().get("fun"),
                        "equal", ctx -> Objects.equals(ctx.args().get(0), ctx.args().get(1)))));
    }

    @Test
    void subExpressionInHash() {
        assertEquals("val is true", render("{{blog (equal (equal true true) true fun='yes')}}",
                Map.of(),
                helpers("blog", ctx -> "val is " + ctx.args().get(0),
                        "equal", ctx -> Objects.equals(ctx.args().get(0), ctx.args().get(1)))));
    }

    @Test
    void subExpressionCanNotBePropertyLookup() {
        // `(bar)` with bar a plain value must fail: it is not a helper.
        assertThrows(IllegalArgumentException.class,
                () -> render("{{foo (bar)}}!", Map.of("bar", "LOL"), helpers("foo", this::foo)));
    }

    // ---- helpers (spec/helpers.js) ----

    @Test
    void helperReturningUndefinedValue() {
        assertEquals(" ", render(" {{nothere}}", Map.of(),
                helpers("nothere", ctx -> null)));
        assertEquals(" ", render(" {{#nothere}}{{/nothere}}", Map.of(),
                helpers("nothere", ctx -> null)));
    }

    @Test
    void blockHelperNewContext() {
        assertEquals("GOODBYE! cruel world!", render("{{#goodbyes}}{{text}}! {{/goodbyes}}cruel {{world}}!",
                Map.of("world", "world"),
                helpers("goodbyes", ctx -> ctx.blockRenderer().apply(Map.of("text", "GOODBYE")))));
    }

    @Test
    void blockHelperSameContext() {
        assertEquals("<form><p>Yehuda</p></form>", render("{{#form}}<p>{{name}}</p>{{/form}}",
                Map.of("name", "Yehuda"),
                helpers("form", ctx -> "<form>" + ctx.blockRenderer().apply(null) + "</form>")));
    }

    @Test
    void blockHelperForUndefinedValue() {
        assertEquals("", render("{{#empty}}shouldn't render{{/empty}}", Map.of(), Map.of()));
    }

    @Test
    void blockHelperPassingComplexPathContext() {
        assertEquals("<form><p>Harold</p></form>", render("{{#form yehuda.cat}}<p>{{name}}</p>{{/form}}",
                Map.of("yehuda", Map.of("name", "Yehuda", "cat", Map.of("name", "Harold"))),
                helpers("form", ctx -> "<form>" + ctx.blockRenderer().apply(ctx.args().get(0)) + "</form>")));
    }

    @Test
    void nestedBlockHelpers() {
        // The link helper is invoked inside the form block (context = yehuda); `this` refers to it
        // (this engine has no depth-based `yehuda` lookup, the context is passed explicitly).
        assertEquals("<form><p>Yehuda</p><a href=\"Yehuda\">Hello</a></form>",
                render("{{#form yehuda}}<p>{{name}}</p>{{#link this}}Hello{{/link}}{{/form}}",
                        Map.of("yehuda", Map.of("name", "Yehuda")),
                        helpers(
                                "link", ctx -> "<a href=\"" + ((Map<?, ?>) ctx.args().get(0)).get("name") + "\">" +
                                        ctx.blockRenderer().apply(ctx.args().get(0)) + "</a>",
                                "form", ctx -> "<form>" + ctx.blockRenderer().apply(ctx.args().get(0)) + "</form>")));
    }

    @Test
    void helpersInNestedContexts() {
        assertEquals("helper", render("{{#outer}}{{#inner}}{{helper}}{{/inner}}{{/outer}}",
                Map.of("outer", Map.of("inner", Map.of("unused", List.of()))),
                helpers("helper", ctx -> "helper")));
    }

    @Test
    void helpersTakePrecedenceOverContext() {
        assertEquals("GOODBYE cruel WORLD", render("{{goodbye}} {{cruel world}}",
                Map.of("goodbye", "goodbye", "world", "world"),
                helpers(
                        "goodbye", ctx -> ((Map<?, ?>) ctx.args().get(0)).get("goodbye").toString().toUpperCase(),
                        "cruel", ctx -> "cruel " + ctx.args().get(0).toString().toUpperCase())));
    }

    @Test
    void scopedNamesTakePrecedenceOverHelpers() {
        assertEquals("goodbye cruel WORLD cruel GOODBYE", render("{{this.goodbye}} {{cruel world}} {{cruel this.goodbye}}",
                Map.of("goodbye", "goodbye", "world", "world"),
                helpers(
                        "goodbye", ctx -> ctx.args().isEmpty() ? null : ctx.args().get(0).toString().toUpperCase(),
                        "cruel", ctx -> "cruel " + ctx.args().get(0).toString().toUpperCase())));
    }

    @Test
    void multipleParamsInline() {
        assertEquals("Message: Goodbye cruel world", render("Message: {{goodbye cruel world}}",
                Map.of("cruel", "cruel", "world", "world"),
                helpers("goodbye", ctx -> "Goodbye " + ctx.args().get(0) + " " + ctx.args().get(1))));
    }

    @Test
    void multipleParamsBlock() {
        assertEquals("Message: Goodbye cruel world", render("Message: {{#goodbye cruel world}}{{greeting}} {{adj}} {{noun}}{{/goodbye}}",
                Map.of("cruel", "cruel", "world", "world"),
                helpers("goodbye", ctx -> ctx.blockRenderer().apply(Map.of(
                        "greeting", "Goodbye", "adj", ctx.args().get(0), "noun", ctx.args().get(1))))));
    }

    @Test
    void helpersHashArgs() {
        assertEquals("GOODBYE CRUEL WORLD 12 TIMES", render("{{goodbye cruel=\"CRUEL\" world=\"WORLD\" times=12}}",
                Map.of(),
                helpers("goodbye", ctx -> "GOODBYE " + ctx.hash().get("cruel") + " " + ctx.hash().get("world") +
                        " " + ctx.hash().get("times") + " TIMES")));
    }

    @Test
    void helpersHashArgsWithBooleans() {
        assertEquals("GOODBYE CRUEL WORLD", render("{{goodbye cruel=\"CRUEL\" world=\"WORLD\" print=true}}",
                Map.of(),
                helpers("goodbye", ctx -> Boolean.TRUE.equals(ctx.hash().get("print")) ?
                        "GOODBYE " + ctx.hash().get("cruel") + " " + ctx.hash().get("world") : "NOT PRINTING")));
        assertEquals("NOT PRINTING", render("{{goodbye cruel=\"CRUEL\" world=\"WORLD\" print=false}}",
                Map.of(),
                helpers("goodbye", ctx -> Boolean.TRUE.equals(ctx.hash().get("print")) ?
                        "GOODBYE " + ctx.hash().get("cruel") + " " + ctx.hash().get("world") : "NOT PRINTING")));
    }

    @Test
    void blockHelperHashArgs() {
        assertEquals("GOODBYE CRUEL world 12 TIMES", render("{{#goodbye cruel=\"CRUEL\" times=12}}world{{/goodbye}}",
                Map.of(),
                helpers("goodbye", ctx -> "GOODBYE " + ctx.hash().get("cruel") + " " +
                        ctx.blockRenderer().apply(null) + " " + ctx.hash().get("times") + " TIMES")));
    }

    @Test
    void decimalNumberLiterals() {
        assertEquals("Message: Hello -1.2 1.2 times", render("Message: {{hello -1.2 1.2}}",
                Map.of(),
                helpers("hello", ctx -> "Hello " + asNum(ctx.args().get(0)) + " " + asNum(ctx.args().get(1)) + " times")));
    }

    @Test
    void negativeNumberLiteral() {
        assertEquals("Message: Hello -12 times", render("Message: {{hello -12}}",
                Map.of(),
                helpers("hello", ctx -> "Hello " + asNum(ctx.args().get(0)) + " times")));
    }

    @Test
    void stringLiterals() {
        assertEquals("Message: Hello world 12 times: true false", render("Message: {{hello \"world\" 12 true false}}",
                Map.of(),
                helpers("hello", ctx -> "Hello " + ctx.args().get(0) + " " + asNum(ctx.args().get(1)) +
                        " times: " + ctx.args().get(2) + " " + ctx.args().get(3))));
        // with single quotes
        assertEquals("Hello world", render("{{hello 'world'}}",
                Map.of(),
                helpers("hello", ctx -> "Hello " + ctx.args().get(0))));
        // apostrophe inside double quotes
        assertEquals("Hello Alan's world", render("{{{hello \"Alan's world\"}}}",
                Map.of(),
                helpers("hello", ctx -> "Hello " + ctx.args().get(0))));
        // escaped quote
        assertEquals("Hello \"world\"", render("{{{hello \"\\\"world\\\"\"}}}",
                Map.of(),
                helpers("hello", ctx -> "Hello " + ctx.args().get(0))));
    }

    @Test
    void malformedBuiltinArgs() {
        assertThrows(IllegalArgumentException.class, () -> compile("{{#if}}{{/if}}"));
        assertThrows(IllegalArgumentException.class, () -> compile("{{#if test \"string\"}}{{/if}}"));
        assertThrows(IllegalArgumentException.class, () -> compile("{{#unless}}{{/unless}}"));
        assertThrows(IllegalArgumentException.class, () -> compile("{{#unless test null}}{{/unless}}"));
        assertThrows(IllegalArgumentException.class, () -> compile("{{#with}}{{/with}}"));
        assertThrows(IllegalArgumentException.class, () -> compile("{{#with test \"string\"}}{{/with}}"));
    }

    @Test
    void unknownHelperCall() {
        assertThrows(IllegalArgumentException.class,
                () -> compile("{{link_to world}}"));
    }

    // ---- ported round 2: ../ paths, block params, inverted sections, SafeString ----

    @Test
    void parentPath() {
        assertEquals("Alan", render("{{#with person}}{{../name}}{{/with}}",
                Map.of("name", "Alan", "person", Map.of("first", "X")), Map.of()));
        assertEquals("Alan Johnson", render("{{#with person}}{{first}} {{../last}}{{/with}}",
                Map.of("last", "Johnson", "person", Map.of("first", "Alan")), Map.of()));
    }

    @Test
    void parentPathInEach() {
        assertEquals("a:root\nb:root", render("{{#each items}}{{this}}:{{../prefix}}{{/each}}",
                Map.of("prefix", "root", "items", List.of("a", "b")), Map.of()));
    }

    @Test
    void parentDataVariable() {
        // @../index = the enclosing each's index; inner each has 2 items so they join with \n
        assertEquals("0;\n1;", render("{{#each items}}{{#each subs}}{{@../index}};{{/each}}{{/each}}",
                Map.of("items", List.of(Map.of("subs", List.of(1)), Map.of("subs", List.of(2)))), Map.of()));
    }

    @Test
    void blockParamsEach() {
        // adapted: each joins with \n and strips trailing whitespace (documented deviation)
        assertEquals("0.a\n1.b", render("{{#each items as |value index|}}{{index}}.{{value}}{{/each}}",
                Map.of("items", List.of("a", "b")), Map.of()));
    }

    @Test
    void blockParamsEachMap() {
        final var map = new java.util.TreeMap<>(Map.of("a", "x", "b", "y"));
        assertEquals("a:x\nb:y", render("{{#each map as |value key|}}{{key}}:{{value}}{{/each}}",
                Map.of("map", map), Map.of()));
    }

    @Test
    void blockParamsWith() {
        assertEquals("Alan", render("{{#with person as |p|}}{{p.first}}{{/with}}",
                Map.of("person", Map.of("first", "Alan")), Map.of()));
    }

    @Test
    void blockParamsNestedShadowing() {
        // inner `as |value|` shadows the outer block param (JS semantics): value = the inner item
        assertEquals("x;\nx;", render(
                "{{#each outer as |value|}}{{#each inner as |value|}}{{value}};{{/each}}{{/each}}",
                Map.of("outer", List.of(1, 2), "inner", List.of("x")), Map.of()));
    }

    @Test
    void blockParamsParentAccess() {
        // `../value` walks up to the outer each frame where the outer block param is bound;
        // the inner each has 2 items so each outer item renders the block twice
        assertEquals("1;\n1;\n2;\n2;", render(
                "{{#each outer as |value|}}{{#each inner as |v|}}{{../value}};{{/each}}{{/each}}",
                Map.of("outer", List.of(1, 2), "inner", List.of("x", "y")), Map.of()));
    }

    @Test
    void invertedSection() {
        assertEquals("nobody", render("{{^people}}nobody{{/people}}", Map.of(), Map.of()));
        assertEquals("", render("{{^people}}nobody{{/people}}", Map.of("people", List.of("a")), Map.of()));
    }

    @Test
    void invertedSectionWithElse() {
        assertEquals("no", render("{{^items}}no{{else}}yes{{/items}}", Map.of("items", List.of()), Map.of()));
        assertEquals("yes", render("{{^items}}no{{else}}yes{{/items}}", Map.of("items", List.of(1)), Map.of()));
    }

    @Test
    void safeString() {
        final var helpers = helpers("raw", ctx -> new io.yupiik.fusion.framework.handlebars.helper.SafeString("<b>hi</b>"));
        assertEquals("<b>hi</b>", render("{{raw}}", Map.of(), helpers)); // SafeString bypasses escaping
        assertEquals("<b>hi</b>", render("{{{raw}}}", Map.of(), helpers));
    }

    @Test
    void safeStringInBlock() {
        final var helpers = helpers("wrap", ctx -> new io.yupiik.fusion.framework.handlebars.helper.SafeString(
                "<div>" + ctx.blockRenderer().apply(null) + "</div>"));
        assertEquals("<div>hi</div>", render("{{#wrap}}hi{{/wrap}}", Map.of(), helpers));
    }

    private String asNum(final Object o) {
        return o == null ? "NaN" : String.valueOf(o);
    }

    private static Map<String, Function<HelperContext, Object>> helpers(
            final String name, final Function<HelperContext, Object> fn) {
        return Map.of(name, fn);
    }

    private static Map<String, Function<HelperContext, Object>> helpers(
            final String name1, final Function<HelperContext, Object> fn1,
            final String name2, final Function<HelperContext, Object> fn2) {
        return Map.of(name1, fn1, name2, fn2);
    }

    private String render(final String template, final Object data, final Map<String, Function<HelperContext, Object>> helpers) {
        return new HandlebarsCompiler(new MapAccessor())
                .compile(new HandlebarsCompiler.CompilationContext(
                        new HandlebarsCompiler.Settings().helpers(helpers), template))
                .render(data);
    }

    private Template compile(final String template) {
        return new HandlebarsCompiler(new MapAccessor())
                .compile(new HandlebarsCompiler.CompilationContext(template));
    }
}