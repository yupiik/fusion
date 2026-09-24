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
import io.yupiik.fusion.framework.handlebars.spi.Accessor;
import io.yupiik.fusion.framework.handlebars.spi.Template;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HandlebarsTest {
    @Test
    void constant() {
        assertRender("foo", Map.of(), "foo");
    }

    @Test
    void simpleVar() {
        assertRender("{{foo}}", Map.of("foo", "the test"), "the test");
    }

    @Test
    void mixedConstantVar() {
        assertRender("foo={{foo}}<", Map.of("foo", "the test"), "foo=the test<");
    }

    @Test
    void mixedConstantUnescapedVar() {
        assertRender("foo={{{foo}}}<", Map.of("foo", "the test"), "foo=the test<");
    }

    @Test
    void unescapedVar() {
        assertRender("{{{foo}}}", Map.of("foo", "& < > \" ' ` ="), "& < > \" ' ` =");
    }

    @Test
    void simpleVarThis() {
        assertRender("{{this}}", "the test", "the test");
    }

    @Test
    void simpleVarEscaped() {
        assertRender(
                "{{foo}}", Map.of("foo", "& < > \" ' ` ="),
                "&amp; &lt; &gt; &quot; &#x27; &#x60; &#x3D;");
    }

    @Test
    void simpleExpressions() {
        assertRender(
                "<p>{{firstname}} {{lastname}}</p>\n",
                Map.of("firstname", "Yehuda", "lastname", "Katz"),
                "<p>Yehuda Katz</p>\n");
    }

    @Test
    void lazyExpressions() {
        assertRender(
                "<p>{{firstname}} {{lastname}}</p>\n",
                Map.of("firstname", () -> "Yehuda", "lastname", (Supplier<String>) () -> "Katz"),
                "<p>Yehuda Katz</p>\n");
    }

    @Test
    void nestedVar() {
        assertRender(
                "{{foo.bar}}",
                Map.of("foo", Map.of("bar", "the test")),
                "the test");
        assertRender("{{foo.bar.dummy}}",
                Map.of("foo", Map.of("bar", Map.of("dummy", "the test"))),
                "the test");
    }

    @Test
    void with() {
        assertRender(
                """
                        {{#with person}}
                        {{firstname}} {{lastname}}
                        {{/with}}""",
                Map.of("person", Map.of("firstname", "Yehuda", "lastname", "Katz")),
                "Yehuda Katz");
    }

    @Test
    void withElse() {
        assertRender(
                """
                        {{#with person}}
                        {{firstname}}
                        {{else}}
                        missing
                        {{/with}}""",
                Map.of(),
                "missing");
    }

    @Test
    void each() {
        assertRender(
                """
                        <ul class="people_list">
                        {{#each people}}
                            <li>{{this}}</li>
                          {{/each}}
                        </ul>""",
                Map.of("people", List.of("Yehuda Katz", "Alan Johnson", "Charles Jolley")),
                """
                        <ul class="people_list">
                            <li>Yehuda Katz</li>
                            <li>Alan Johnson</li>
                            <li>Charles Jolley</li>
                        </ul>""");
    }

    @Test
    void listDataVariables() {
        assertRender(
                """
                        <ul class="people_list">
                        {{#each people}}
                            <li>{{this}}:{{#if @first}} first,{{/if}}{{#if @last}} last,{{/if}} index={{@index}}</li>
                          {{/each}}
                        </ul>""",
                Map.of("people", List.of("Yehuda Katz", "Alan Johnson", "Charles Jolley")),
                """
                        <ul class="people_list">
                            <li>Yehuda Katz: first, index=0</li>
                            <li>Alan Johnson: index=1</li>
                            <li>Charles Jolley: last, index=2</li>
                        </ul>""");
    }

    @Test
    void listMapEntryDataVariables() {
        final var people = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        people.putAll(Map.of(
                "Alan", "Johnson",
                "Charles", "Jolley",
                "Yehuda", "Katz"));

        assertRender(
                """
                        <ul class="people_list">
                        {{#each people}}
                            <li>firstname={{@key}} lastname={{@value}}:{{#if @first}} first,{{/if}}{{#if @last}} last,{{/if}} index={{@index}}</li>
                          {{/each}}
                        </ul>""",
                Map.of("people", people),
                """
                        <ul class="people_list">
                            <li>firstname=Alan lastname=Johnson: first, index=0</li>
                            <li>firstname=Charles lastname=Jolley: index=1</li>
                            <li>firstname=Yehuda lastname=Katz: last, index=2</li>
                        </ul>""");
    }

    @Test
    void unless() {
        assertRender(
                """
                        <div class="entry">
                        {{#unless license}}
                        <h3 class="warning">WARNING: This entry does not have a license!</h3>
                        {{/unless}}
                        </div>""",
                Map.of(),
                """
                        <div class="entry">
                        <h3 class="warning">WARNING: This entry does not have a license!</h3>
                        </div>""");
    }

    @Test
    void ifBuiltIn() {
        assertRender(
                """
                        <div class="entry">
                        {{#if author}}
                        <h1>{{firstName}} {{lastName}}</h1>
                        {{/if}}
                        </div>""",
                Map.of("author", true, "firstName", "Yehuda", "lastName", "Katz"),
                """
                        <div class="entry">
                        <h1>Yehuda Katz</h1>
                        </div>""");
    }

    @Test
    void comments() {
        assertRender("""
                        {{! This comment will not show up in the output}}
                        <!-- This comment will show up as HTML-comment -->
                        {{!-- This comment may contain mustaches like }} --}}
                        """,
                Map.of(),
                """
                        <!-- This comment will show up as HTML-comment -->
                        """);
    }

    @Test
    void inlineHelper() {
        assertRender(
                "{{firstname}} {{loud lastname}}",
                Map.of("firstname", "Yehuda", "lastname", "Katz"),
                "Yehuda KATZ");
    }

    @Test
    void inlineHelperList() {
        assertRender(
                "{{list_helper lastname 3}}",
                Map.of("lastname", "Katz"),
                "Katz: java.lang.String, 3: java.lang.Integer");
    }

    @Test
    void specificAccessor() {
        assertRender(
                "{{firstname}} {{loud lastname}}",
                new Person("Yehuda", "Katz"),
                "Yehuda KATZ",
                (data, name) -> { // custom record specific accessor - specific for this template
                    if (!(data instanceof Person p)) {
                        throw new IllegalArgumentException("Unsupported data: " + data);
                    }
                    return switch (name) {
                        case "firstname" -> p.firstname();
                        case "lastname" -> p.lastname();
                        default -> "missing accessor";
                    };
                });
    }

    @Test
    void thisHelper() {
        assertRender(
                """
                        {{#each people}}
                           {{print_person}}
                        {{/each}}
                        """,
                Map.of(
                        "people", List.of(
                                Map.of("firstname", "Nils", "lastname", "Knappmeier"),
                                Map.of("firstname", "Yehuda", "lastname", "Katz"))),
                """
                           Nils Knappmeier
                           Yehuda Katz
                        """);
    }

    @Test
    void blockHelper() {
        assertRender(
                """
                        {{#list people}}{{firstname}} {{lastname}}{{/list}}
                        """,
                Map.of(
                        "people", List.of(
                                Map.of("firstname", "Yehuda", "lastname", "Katz"),
                                Map.of("firstname", "Carl", "lastname", "Lerche"),
                                Map.of("firstname", "Alan", "lastname", "Johnson"))),
                """
                        <ul>
                        <li>Yehuda Katz</li>
                        <li>Carl Lerche</li>
                        <li>Alan Johnson</li>
                        </ul>
                        """);
    }

    @Test
    void partials() {
        assertRender(
                """
                        {{#each people}}
                          {{>person person=.}}
                        {{/each}}""",
                Map.of(
                        "people", List.of(
                                Map.of("name", "Nils", "age", 20),
                                Map.of("name", "Teddy", "age", 10),
                                Map.of("name", "Nelson", "age", 40))),
                """
                        \s Nils is 20 years old.
                          Teddy is 10 years old.
                          Nelson is 40 years old.""");
    }

    @Test
    void nestedEach() {
        assertRender(
                "{{#each items}}{{{metadata.name}}}: {{#each spec.ports}}{{{nodePort}}}{{/each}}{{/each}}",
                Map.of(
                        "items", List.of(
                                Map.of("metadata", Map.of("name", "s1"), "spec", Map.of("ports", List.of(Map.of("nodePort", 1)))),
                                Map.of("metadata", Map.of("name", "s2"), "spec", Map.of("ports", List.of(Map.of("nodePort", 2)))),
                                Map.of("metadata", Map.of("name", "s3"), "spec", Map.of("ports", List.of(Map.of("nodePort", 3)))))),
                """
                        s1: 1
                        s2: 2
                        s3: 3""");
    }

    @Test
    void eachComplex() {
        assertRender(
                "{{#each foo.bar}}{{{dummy.id}}}{{/each}}",
                Map.of(
                        "foo", Map.of(
                                "bar", List.of(
                                        Map.of("dummy", Map.of("id", "1")),
                                        Map.of("dummy", Map.of("id", "2"))
                                ))),
                """
                        1
                        2""");
    }

    @Test
    void parentAccessor() {
        assertRender(
                "{{#each items}}{{#each spec.ports}}{{{metadata.name}}} ({{#if name}}{{{name}}}{{/if}}{{#unless name}}{{@index}}{{/unless}}): {{{nodePort}}}{{/each}}{{/each}}",
                Map.of(
                        "items", List.of(
                                Map.of("metadata", Map.of("name", "s1"), "spec", Map.of("ports", List.of(Map.of("nodePort", 1)))),
                                Map.of("metadata", Map.of("name", "s2"), "spec", Map.of("ports", List.of(Map.of("name", "second", "nodePort", 2)))),
                                Map.of("metadata", Map.of("name", "s3"), "spec", Map.of("ports", List.of(
                                        Map.of("nodePort", 3),
                                        Map.of("nodePort", 4, "name", "last")))))),
                """
                        s1 (0): 1
                        s2 (second): 2
                        s3 (0): 3
                        s3 (last): 4""");
    }

    // ---- new features ----

    @Test
    void eachElse() {
        assertRender(
                "{{#each people}}<li>{{this}}</li>{{else}}empty{{/each}}",
                Map.of("people", List.of()),
                "empty");
        assertRender(
                "{{#each people}}<li>{{this}}</li>{{else}}empty{{/each}}",
                Map.of(),
                "empty");
    }

    @Test
    void eachLimitOffset() {
        assertRender(
                "{{#each people limit=2}}{{this}}{{/each}}",
                Map.of("people", List.of("a", "b", "c")),
                "a\nb");
        assertRender(
                "{{#each people offset=1 limit=1}}{{this}}{{/each}}",
                Map.of("people", List.of("a", "b", "c")),
                "b");
        assertRender(
                "{{#each people limit=0}}{{this}}{{else}}empty{{/each}}",
                Map.of("people", List.of("a", "b")),
                "empty");
        assertRender(
                "{{#each people offset=10}}{{this}}{{else}}empty{{/each}}",
                Map.of("people", List.of("a", "b")),
                "empty");
    }

    @Test
    void unlessFalse() {
        assertRender(
                "{{#unless license}}no license{{else}}licensed{{/unless}}",
                Map.of("license", false),
                "no license");
    }

    @Test
    void ifElse() {
        assertRender(
                "{{#if author}}yes{{else}}no{{/if}}",
                Map.of("author", true),
                "yes");
        assertRender(
                "{{#if author}}yes{{else}}no{{/if}}",
                Map.of(),
                "no");
        assertRender(
                "{{#if author}}yes{{else}}no{{/if}}",
                Map.of("author", false),
                "no");
    }

    @Test
    void elseIfChain() {
        assertRender(
                "{{#if a}}A{{else if b}}B{{else}}C{{/if}}",
                Map.of("a", true),
                "A");
        assertRender(
                "{{#if a}}A{{else if b}}B{{else}}C{{/if}}",
                Map.of("b", true),
                "B");
        assertRender(
                "{{#if a}}A{{else if b}}B{{else}}C{{/if}}",
                Map.of(),
                "C");
    }

    @Test
    void nestedElse() {
        assertRender(
                "{{#if a}}{{#each xs}}{{this}}{{else}}inner empty{{/each}}{{else}}outer no{{/if}}",
                Map.of("a", true, "xs", List.of()),
                "inner empty");
        assertRender(
                "{{#if a}}{{#each xs}}{{this}}{{else}}inner empty{{/each}}{{else}}outer no{{/if}}",
                Map.of(),
                "outer no");
    }

    @Test
    void eqBlockHelper() {
        assertRenderDefaults(
                "{{#eq a b}}equal{{else}}different{{/eq}}",
                Map.of("a", 1, "b", 1),
                "equal");
        assertRenderDefaults(
                "{{#eq a b}}equal{{else}}different{{/eq}}",
                Map.of("a", 1, "b", 2),
                "different");
    }

    @Test
    void ifSubExpression() {
        assertRenderDefaults(
                "{{#if (eq a b)}}equal{{else}}different{{/if}}",
                Map.of("a", 1, "b", 1),
                "equal");
        assertRenderDefaults(
                "{{#if (eq a b)}}equal{{else}}different{{/if}}",
                Map.of("a", 1, "b", 2),
                "different");
    }

    @Test
    void nestedSubExpression() {
        assertRenderDefaults(
                "{{#if (and (eq a 1) (gt b 0))}}yes{{else}}no{{/if}}",
                Map.of("a", 1, "b", 5),
                "yes");
        assertRenderDefaults(
                "{{#if (and (eq a 1) (gt b 0))}}yes{{else}}no{{/if}}",
                Map.of("a", 2, "b", 5),
                "no");
    }

    @Test
    void eachSubExpression() {
        assertRender(
                "{{#each (items xs)}}{{this}}{{/each}}",
                Map.of("xs", List.of("a", "b")),
                "a\nb");
    }

    @Test
    void defaultHelpers() {
        assertRenderDefaults("{{eq 1 1}}", Map.of(), "true");
        assertRenderDefaults("{{eq 1 2}}", Map.of(), "false");
        assertRenderDefaults("{{ne 1 2}}", Map.of(), "true");
        assertRenderDefaults("{{gt 2 1}}", Map.of(), "true");
        assertRenderDefaults("{{gte 2 2}}", Map.of(), "true");
        assertRenderDefaults("{{lt 1 2}}", Map.of(), "true");
        assertRenderDefaults("{{lte 2 2}}", Map.of(), "true");
        assertRenderDefaults("{{and true 1}}", Map.of(), "true");
        assertRenderDefaults("{{or false 1}}", Map.of(), "true");
        assertRenderDefaults("{{not false}}", Map.of(), "true");
        assertRenderDefaults("{{default missing \"fallback\"}}", Map.of(), "fallback");
        assertRenderDefaults("{{lookup person \"name\"}}", Map.of("person", Map.of("name", "Ada")), "Ada");
    }

    @Test
    void eqNumbersOfDifferentTypes() {
        assertRenderDefaults("{{eq a b}}", Map.of("a", 1, "b", 1L), "true");
        assertRenderDefaults("{{gt a b}}", Map.of("a", 10, "b", 5.5), "true");
        assertRenderDefaults("{{lt \"5\" 10}}", Map.of(), "true");
    }

    @Test
    void timeHelper() {
        final var instant = Instant.parse("2023-04-05T06:07:08Z");
        final var defaultPattern = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
        assertRender(
                "{{time value}}",
                Map.of("value", instant.toEpochMilli()),
                defaultPattern.format(instant));

        assertRender(
                "{{time value pattern=\"yyyy-MM-dd\"}}",
                Map.of("value", instant),
                "2023-04-05");

        assertRender(
                "{{time (lookup values \"date\") pattern=\"HH:mm\"}}",
                Map.of("values", Map.of("date", LocalDateTime.parse("2023-04-05T06:07:08"))),
                "06:07");

        // 0-arg form = now
        final var now = LocalDate.now();
        assertRender(
                "{{time pattern=\"yyyy\"}}",
                Map.of(),
                String.valueOf(now.getYear()));
    }

    @Test
    void partialLiteralParam() {
        assertRender(
                "{{>person2 name=\"Nils\" age=20}}",
                Map.of(),
                "Nils is 20 years old.");
    }

    @Test
    void userHelperOverridesDefault() {
        assertEquals(
                "overridden",
                new HandlebarsCompiler(new MapAccessor())
                        .compile(new HandlebarsCompiler.CompilationContext(
                                new HandlebarsCompiler.Settings()
                                        .helpers(Map.of("eq", ctx -> "overridden"))
                                        .partials(partialsTemplates()),
                                "{{eq a b}}"))
                        .render(Map.of("a", "x", "b", "y")));
    }

    // ---- raw blocks ----

    @Test
    void rawBlock() {
        assertRender(
                "{{{{hbs}}}}{{else}}{{{{/hbs}}}}",
                Map.of(),
                "{{else}}");
        assertRender(
                "{{{{hbs}}}}#if (eq a b){{{{/hbs}}}}",
                Map.of(),
                "#if (eq a b)");
    }

    @Test
    void rawBlockVerbatim() {
        assertRender(
                "{{{{x}}}} a {{b}} c {{{{/x}}}}",
                Map.of("b", "resolved"),
                " a {{b}} c ");
    }

    @Test
    void rawBlockUnclosed() {
        assertThrows(IllegalArgumentException.class, () -> assertCompile("{{{{x}} a {{b}}"));
    }

    @Test
    void rawBlockMissingEnd() {
        assertThrows(IllegalArgumentException.class, () -> assertCompile("{{{{x}}}} a {{b}}"));
    }

    // ---- error cases ----

    @Test
    void mismatchedBlockClose() {
        assertThrows(IllegalArgumentException.class, () -> assertCompile("{{#if a}}{{/each}}"));
    }

    @Test
    void topLevelElse() {
        assertThrows(IllegalArgumentException.class, () -> assertCompile("{{else}}"));
    }

    @Test
    void unknownSectionKeyword() {
        assertThrows(IllegalArgumentException.class, () -> assertCompile("{{#unknown a}}x{{/unknown}}"));
    }

    @Test
    void unknownHashEach() {
        assertThrows(IllegalArgumentException.class, () -> assertCompile("{{#each people size=2}}x{{/each}}"));
    }

    @Test
    void hashOnIf() {
        assertThrows(IllegalArgumentException.class, () -> assertCompile("{{#if a size=2}}x{{/if}}"));
    }

    private void assertRender(final String resource, final Object data, final String expected) {
        assertRender(resource, data, expected, new MapAccessor());
    }

    private void assertRender(final String resource, final Object data, final String expected, final Accessor accessor) {
        assertRender(resource, data, expected,
                new HandlebarsCompiler.Settings().helpers(helpers()).partials(partialsTemplates()), accessor);
    }

    private void assertRenderDefaults(final String resource, final Object data, final String expected) {
        assertRender(resource, data, expected,
                new HandlebarsCompiler.Settings().partials(partialsTemplates()), new MapAccessor());
    }

    private void assertRender(final String resource, final Object data, final String expected,
                              final HandlebarsCompiler.Settings settings, final Accessor accessor) {
        assertEquals(
                expected,
                new HandlebarsCompiler(accessor)
                        .compile(new HandlebarsCompiler.CompilationContext(settings, resource))
                        .render(data));
    }

    private Template assertCompile(final String resource) {
        return new HandlebarsCompiler(new MapAccessor())
                .compile(new HandlebarsCompiler.CompilationContext(
                        new HandlebarsCompiler.Settings()
                                .helpers(helpers())
                                .partials(partialsTemplates()),
                        resource));
    }

    private Map<String, String> partialsTemplates() {
        return Map.of(
                "person", "{{person.name}} is {{person.age}} years old.",
                "person2", "{{name}} is {{age}} years old.");
    }

    private Map<String, Function<HelperContext, Object>> helpers() {
        return Map.of(
                "loud", ctx -> ctx.args().isEmpty() ? null : ctx.args().get(0).toString().toUpperCase(ROOT),
                "print_person", ctx -> ctx.args().isEmpty() ? null :
                        ctx.args().get(0) instanceof Map<?, ?> map ? map.get("firstname") + " " + map.get("lastname") : "failed, not a map",
                "list_helper", ctx -> ctx.args().stream()
                        .map(it -> it + ": " + it.getClass().getName())
                        .collect(joining(", ")),
                "list", ctx -> ctx.args().isEmpty() || !(ctx.args().get(0) instanceof Collection<?> list) ? "failed, not a list" :
                        list.isEmpty() ? (ctx.inverseRenderer() == null ? "" : ctx.inverseRenderer().apply(ctx.args().get(0))) :
                                list.stream()
                                        .map(ctx.blockRenderer())
                                        .map(it -> "<li>" + it + "</li>")
                                        .collect(joining("\n", "<ul>\n", "\n</ul>")),
                "items", ctx -> ctx.args().isEmpty() ? null : ctx.args().get(0));
    }

    private record Person(String firstname, String lastname) {
    }
}