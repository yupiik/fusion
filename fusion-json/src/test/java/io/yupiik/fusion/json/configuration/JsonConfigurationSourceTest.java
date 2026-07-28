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
package io.yupiik.fusion.json.configuration;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Properties;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonConfigurationSourceTest {
    @Test
    void nullValue() {
        final var source = new JsonConfigurationSource(() -> new StringReader("null"));
        assertNull(source.get(""));
    }

    @Test
    void booleanTrue() {
        final var source = new JsonConfigurationSource(() -> new StringReader("true"));
        assertEquals("true", source.get(""));
    }

    @Test
    void booleanFalse() {
        final var source = new JsonConfigurationSource(() -> new StringReader("false"));
        assertEquals("false", source.get(""));
    }

    @Test
    void stringValue() {
        final var source = new JsonConfigurationSource(() -> new StringReader("\"hello\""));
        assertEquals("hello", source.get(""));
    }

    @Test
    void bigDecimalValue() {
        final var source = new JsonConfigurationSource(() -> new StringReader("42"));
        assertEquals("42", source.get(""));
    }

    @Test
    void bigDecimalFraction() {
        final var source = new JsonConfigurationSource(() -> new StringReader("3.14"));
        assertEquals("3.14", source.get(""));
    }

    @Test
    void mapWithPrimitiveValues() {
        final var source = new JsonConfigurationSource(() -> new StringReader("{\"b\": \"2\", \"a\": \"1\"}"));
        assertEquals("1", source.get("a"));
        assertEquals("2", source.get("b"));
    }

    @Test
    void mapWithNumberValues() {
        final var source = new JsonConfigurationSource(() -> new StringReader("{\"b\": 2, \"a\": 1}"));
        assertEquals("1", source.get("a"));
        assertEquals("2", source.get("b"));
    }

    @Test
    void mapWithMixedPrimitiveValues() {
        final var source = new JsonConfigurationSource(() -> new StringReader("{\"s\": \"str\", \"b\": true, \"n\": 42}"));
        assertEquals("true", source.get("b"));
        assertEquals("42", source.get("n"));
        assertEquals("str", source.get("s"));
    }

    @Test
    void mapWithNestedObject() {
        final var json = "{\"a\": \"1\", \"b\": {\"c\": \"2\"}}";
        final var source = new JsonConfigurationSource(() -> new StringReader(json));
        assertEquals("1", source.get("a"));
        assertEquals("2", source.get("b.c"));
    }

    @Test
    void mapWithMultipleNestedObjects() {
        final var json = "{\"a\": {\"x\": \"1\"}, \"b\": {\"y\": \"2\"}}";
        final var source = new JsonConfigurationSource(() -> new StringReader(json));
        assertEquals("1", source.get("a.x"));
        assertEquals("2", source.get("b.y"));
    }

    @Test
    void mapWithDeeplyNestedObject() {
        final var json = "{\"a\": \"1\", \"b\": {\"c\": {\"d\": \"2\"}}}";
        final var source = new JsonConfigurationSource(() -> new StringReader(json));
        assertEquals("1", source.get("a"));
        assertEquals("2", source.get("b.c.d"));
    }

    @Test
    void collectionOfPrimitives() {
        final var source = new JsonConfigurationSource(() -> new StringReader("[1, 2, 3]"));
        assertEquals("1, 2, 3", source.get(""));
    }

    @Test
    void collectionOfStrings() {
        final var source = new JsonConfigurationSource(() -> new StringReader("[\"a\", \"b\", \"c\"]"));
        assertEquals("a, b, c", source.get(""));
    }

    @Test
    void collectionOfPrimitivesMixedTypes() {
        final var source = new JsonConfigurationSource(() -> new StringReader("[true, 42, \"hello\"]"));
        assertEquals("true, 42, hello", source.get(""));
    }

    @Test
    void collectionOfObjects() {
        final var json = "[{\"x\": \"1\"}, {\"x\": \"2\"}]";
        final var source = new JsonConfigurationSource(() -> new StringReader(json));
        assertEquals("2", source.get("length"));
        assertEquals("1", source.get("0.x"));
        assertEquals("2", source.get("1.x"));
    }

    @Test
    void collectionOfMixedPrimitivesAndObjects() {
        final var json = "[1, {\"x\": \"2\"}]";
        final var source = new JsonConfigurationSource(() -> new StringReader(json));
        assertEquals("2", source.get("length"));
        assertEquals("1", source.get("0"));
        assertEquals("2", source.get("1.x"));
    }

    @Test
    void collectionOfCollections() {
        final var json = "[[1, 2], [3, 4]]";
        final var source = new JsonConfigurationSource(() -> new StringReader(json));
        assertEquals("2", source.get("length"));
        assertEquals("1, 2", source.get("0"));
        assertEquals("3, 4", source.get("1"));
    }

    @Test
    void emptyCollection() {
        final var source = new JsonConfigurationSource(() -> new StringReader("[]"));
        assertEquals("0", source.get("length"));
    }

    @Test
    void emptyMap() {
        final var source = new JsonConfigurationSource(() -> new StringReader("{}"));
        assertNull(source.get(""));
    }

    @Test
    void mapWithNullValues() {
        final var source = new JsonConfigurationSource(() -> new StringReader("{\"a\": null, \"b\": \"2\"}"));
        assertNull(source.get("a"));
        assertEquals("2", source.get("b"));
    }

    @Test
    void collectionWithNulls() {
        final var source = new JsonConfigurationSource(() -> new StringReader("[null, \"a\", null]"));
        assertEquals("a", source.get(""));
    }

    @Test
    void mapWithCollectionValues() {
        final var json = "{\"a\": \"1\", \"b\": [1, 2]}";
        final var source = new JsonConfigurationSource(() -> new StringReader(json));
        assertEquals("1", source.get("a"));
        assertEquals("1, 2", source.get("b"));
    }

    @Test
    void mapWithCollectionOfObjectsValues() {
        final var json = "{\"a\": \"1\", \"b\": [{\"x\": \"2\"}, {\"x\": \"3\"}]}";
        final var source = new JsonConfigurationSource(() -> new StringReader(json));
        assertEquals("1", source.get("a"));
        assertEquals("2", source.get("b.length"));
        assertEquals("2", source.get("b.0.x"));
        assertEquals("3", source.get("b.1.x"));
    }

    @Test
    void nestedStructureWithMapAndCollectionAndPrimitives() {
        final var json = """
                {
                  "stringProp": "hello",
                  "numberProp": 42,
                  "boolProp": true,
                  "objProp": {
                    "nested": "value"
                  },
                  "listProp": [
                    {"name": "first"},
                    {"name": "second"}
                  ],
                  "mapProp": [
                    {"key": "k1", "value": {"data": "v1"}},
                    {"key": "k2", "value": {"data": "v2"}}
                  ]
                }""";
        final var source = new JsonConfigurationSource(() -> new StringReader(json));

        // primitives
        assertEquals("hello", source.get("stringProp"));
        assertEquals("42", source.get("numberProp"));
        assertEquals("true", source.get("boolProp"));

        // nested object
        assertEquals("value", source.get("objProp.nested"));

        // list of objects
        assertEquals("2", source.get("listProp.length"));
        assertEquals("first", source.get("listProp.0.name"));
        assertEquals("second", source.get("listProp.1.name"));

        // map representation (array of key/value objects)
        assertEquals("2", source.get("mapProp.length"));
        assertEquals("k1", source.get("mapProp.0.key"));
        assertEquals("v1", source.get("mapProp.0.value.data"));
        assertEquals("k2", source.get("mapProp.1.key"));
        assertEquals("v2", source.get("mapProp.1.value.data"));
    }

    @Test
    void readerConstructor() {
        try (final var reader = new StringReader("true")) {
            final var source = new JsonConfigurationSource(reader);
            assertEquals("true", source.get(""));
        }
    }

    @Test
    void propertyAccessPatternForListOfPrimitives() {
        final var source = new JsonConfigurationSource(() -> new StringReader("{\"tags\": \"a,b,c\"}"));
        assertEquals("a,b,c", source.get("tags"));
    }

    @Test
    void propertyAccessPatternForMapOfPrimitives() {
        final var source = new JsonConfigurationSource(() -> new StringReader("{\"metadata\": \"key1=value1\\nkey2=value2\"}"));
        assertEquals("key1=value1\nkey2=value2", source.get("metadata"));
    }

    @Test
    void propertyAccessPatternForListOfObjects() {
        final var json = "{\"servers\": [{\"host\": \"localhost\", \"port\": 8080}]}";
        final var source = new JsonConfigurationSource(() -> new StringReader(json));
        assertEquals("1", source.get("servers.length"));
        assertEquals("localhost", source.get("servers.0.host"));
        assertEquals("8080", source.get("servers.0.port"));
    }

    @Test
    void propertyAccessPatternForMapOfObjects() {
        final var json = "{\"servers\": [{\"key\": \"primary\", \"value\": {\"host\": \"localhost\", \"port\": 8080}}]}";
        final var source = new JsonConfigurationSource(() -> new StringReader(json));
        assertEquals("1", source.get("servers.length"));
        assertEquals("primary", source.get("servers.0.key"));
        assertEquals("localhost", source.get("servers.0.value.host"));
        assertEquals("8080", source.get("servers.0.value.port"));
    }

    @Test
    void asListTrueAllPrimitives() {
        final var json = "{\"$asList\": true, \"host\": \"localhost\", \"port\": 8080}";
        final var source = new JsonConfigurationSource(() -> new StringReader(json));
        final var value = source.get("");
        final var props = new Properties();
        try (final var reader = new StringReader(value)) {
            props.load(reader);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        assertEquals("localhost", props.getProperty("host"));
        assertEquals("8080", props.getProperty("port"));
        assertEquals(2, props.size());
    }

    @Test
    void asListTrueWithObjects() {
        final var json = "{\"$asList\": true, \"server\": {\"host\": \"localhost\", \"port\": 8080}, \"client\": {\"host\": \"other\"}}";
        final var source = new JsonConfigurationSource(() -> new StringReader(json));
        assertEquals("2", source.get("length"));
        assertEquals("server", source.get("0.key"));
        assertEquals("localhost", source.get("0.value.host"));
        assertEquals("8080", source.get("0.value.port"));
        assertEquals("client", source.get("1.key"));
        assertEquals("other", source.get("1.value.host"));
    }

    @Test
    void asListFalseBehavesLikeAbsent() {
        final var json = "{\"$asList\": false, \"host\": \"localhost\", \"port\": 8080}";
        final var source = new JsonConfigurationSource(() -> new StringReader(json));
        assertEquals("localhost", source.get("host"));
        assertEquals("8080", source.get("port"));
    }

    @Test
    void asListTrueNestedAsList() {
        final var json = "{\"outer\": {\"$asList\": true, \"key1\": {\"name\": \"a\"}, \"key2\": {\"name\": \"b\"}}}";
        final var source = new JsonConfigurationSource(() -> new StringReader(json));
        assertEquals("2", source.get("outer.length"));
        assertEquals("key1", source.get("outer.0.key"));
        assertEquals("a", source.get("outer.0.value.name"));
        assertEquals("key2", source.get("outer.1.key"));
        assertEquals("b", source.get("outer.1.value.name"));
    }

    @Test
    void asListTrueNestedObjects() {
        final var json = "{\"$asList\": true, \"outer\": {\"inner\": \"value\"}, \"other\": {\"nested\": {\"deep\": \"true\"}}}";
        final var source = new JsonConfigurationSource(() -> new StringReader(json));
        assertEquals("2", source.get("length"));
        assertEquals("outer", source.get("0.key"));
        assertEquals("value", source.get("0.value.inner"));
        assertEquals("other", source.get("1.key"));
        assertEquals("true", source.get("1.value.nested.deep"));
    }

    @Test
    void asListTrueWithMixedPrimitivesAndObjects() {
        final var json = "{\"$asList\": true, \"name\": \"test\", \"config\": {\"enabled\": true}}";
        final var source = new JsonConfigurationSource(() -> new StringReader(json));
        assertEquals("2", source.get("length"));
        assertEquals("name", source.get("0.key"));
        assertEquals("test", source.get("0.value"));
        assertEquals("config", source.get("1.key"));
        assertEquals("true", source.get("1.value.enabled"));
    }

    @Test
    void keyNormalizerWithHyphenToDotReaderSupplierConstructor() {
        final var normalizer = (Function<String, String>) key -> key.replace('-', '.');
        final var source = new JsonConfigurationSource(() -> new StringReader("{\"my-key\": \"value\"}"), normalizer);
        assertEquals("value", source.get("my.key"));
    }

    @Test
    void keyNormalizerWithHyphenToDotReaderConstructor() {
        final var normalizer = (Function<String, String>) key -> key.replace('-', '.');
        try (final var reader = new StringReader("{\"my-key\": \"value\"}")) {
            final var source = new JsonConfigurationSource(reader, normalizer);
            assertEquals("value", source.get("my.key"));
        }
    }

    @Test
    void keyNormalizerWithHyphenToDotNestedKeys() {
        final var normalizer = (Function<String, String>) key -> key.replace('-', '.');
        final var source = new JsonConfigurationSource(() -> new StringReader("{\"my-key\": {\"nested-prop\": \"value\"}}"), normalizer);
        assertEquals("value", source.get("my.key.nested.prop"));
    }

    @Test
    void keyNormalizerWithHyphenToDotMixedDotsAndHyphens() {
        final var normalizer = (Function<String, String>) key -> key.replace('-', '.');
        final var source = new JsonConfigurationSource(() -> new StringReader("{\"my-key\": {\"nested.prop\": \"value\"}}"), normalizer);
        assertEquals("value", source.get("my.key.nested.prop"));
    }

    @Test
    void keyNormalizerCliAppFlag() {
        final var normalizer = (Function<String, String>) key -> key.replace('-', '.');
        final var json = "{\"app-log-level\": \"debug\", \"app-feature-flag\": \"enabled\"}";
        final var source = new JsonConfigurationSource(() -> new StringReader(json), normalizer);
        assertEquals("debug", source.get("app.log.level"));
        assertEquals("enabled", source.get("app.feature.flag"));
    }

    @Test
    void keyNormalizerWithCollection() {
        final var normalizer = (Function<String, String>) key -> key.replace('-', '.');
        final var json = "{\"server-list\": [{\"host-name\": \"localhost\", \"server-port\": 8080}]}";
        final var source = new JsonConfigurationSource(() -> new StringReader(json), normalizer);
        assertEquals("1", source.get("server.list.length"));
        assertEquals("localhost", source.get("server.list.0.host.name"));
        assertEquals("8080", source.get("server.list.0.server.port"));
    }

    @Test
    void keyNormalizerIdentityPreservesKeys() {
        final var source = new JsonConfigurationSource(() -> new StringReader("{\"my-key\": \"value\"}"), Function.identity());
        assertEquals("value", source.get("my-key"));
    }

    @Test
    void keyNormalizerReaderConstructorClosesReader() {
        final var normalizer = (Function<String, String>) key -> key.replace('-', '.');
        final var reader = new StringReader("{\"test-flag\": \"true\"}") {
            private boolean closed;

            @Override
            public void close() {
                closed = true;
                super.close();
            }

            @Override
            public String toString() {
                return "closed: " + closed;
            }
        };
        final var source = new JsonConfigurationSource(reader, normalizer);
        assertEquals("true", source.get("test.flag"));
    }
}
