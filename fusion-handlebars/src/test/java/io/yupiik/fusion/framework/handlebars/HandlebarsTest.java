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
import io.yupiik.fusion.framework.handlebars.helper.BlockHelperContext;
import io.yupiik.fusion.framework.handlebars.spi.Accessor;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
                Map.of("firstname", (Supplier<String>) () -> "Yehuda", "lastname", (Supplier<String>) () -> "Katz"),
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

    private void assertRender(final String resource, final Object data, final String expected) {
        assertRender(resource, data, expected, new MapAccessor());
    }

    private void assertRender(final String resource, final Object data, final String expected, final Accessor accessor) {
        assertEquals(
                expected,
                new HandlebarsCompiler(accessor)
                        .compile(new HandlebarsCompiler.CompilationContext(
                                new HandlebarsCompiler.Settings()
                                        .helpers(helpers())
                                        .partials(partialsTemplates()),
                                resource))
                        .render(data));
    }

    private Map<String, String> partialsTemplates() {
        return Map.of(
                "person", "{{person.name}} is {{person.age}} years old.");
    }

    private Map<String, Function<Object, String>> helpers() {
        return Map.of(
                "loud", o -> o.toString().toUpperCase(ROOT),
                "print_person", o -> o instanceof Map<?, ?> map ? map.get("firstname") + " " + map.get("lastname") : "failed, not a map",
                "list", o -> o instanceof BlockHelperContext ctx && ctx.data() instanceof Collection<?> list ?
                        list.stream()
                                .map(ctx.blockRenderer())
                                .map(it -> "<li>" + it + "</li>")
                                .collect(joining("\n", "<ul>\n", "\n</ul>")) : "failed, not a list");
    }

    private record Person(String firstname, String lastname) {
    }
}
