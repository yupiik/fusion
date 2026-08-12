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
package io.yupiik.fusion.json.schema;

import io.yupiik.fusion.framework.api.configuration.impl.MapConfigSource;
import io.yupiik.fusion.framework.api.container.configuration.ConfigurationImpl;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.yupiik.fusion.json.schema.JsonSchemaService.SCHEMA_2020_12;
import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SuppressWarnings({"unchecked", "cast"})
class JsonSchemaServiceTest {
    private final JsonSchemaService service = new JsonSchemaService();

    @Test
    void simpleNullableStringBecomesTypeArray() {
        final var out = service.toJsonSchema202012(Map.of("type", "string", "nullable", true, "title", "a"));
        assertEquals(Map.of(
                "$schema", SCHEMA_2020_12,
                "type", List.of("string", "null"),
                "title", "a"), out);
    }

    @Test
    void nullableFalseIsDroppedKeepingPlainType() {
        final var out = service.toJsonSchema202012(Map.of("type", "integer", "nullable", false));
        assertEquals(Map.of("$schema", SCHEMA_2020_12, "type", "integer"), out);
    }

    @Test
    void absentNullableIsIdempotent() {
        final var out = service.toJsonSchema202012(Map.of("type", "object", "properties", Map.of("id", Map.of("type", "string"))));
        assertEquals(Map.of(
                "$schema", SCHEMA_2020_12,
                "type", "object",
                "properties", Map.of("id", Map.of("type", "string"))), out);
    }

    @Test
    void nullableOnExistingTypeArrayAppendsNull() {
        final var out = service.toJsonSchema202012(Map.of("type", List.of("string", "number"), "nullable", true));
        assertEquals(List.of("string", "number", "null"), out.get("type"));
    }

    @Test
    void nullableWithoutTypeIsNull() {
        final var out = service.toJsonSchema202012(Map.of("nullable", true));
        assertEquals("null", out.get("type"));
    }

    @Test
    void nullableRefWithoutTypeIsUnchangedRef() {
        final var out = service.toJsonSchema202012(Map.of("$ref", "#/$defs/X", "nullable", true));
        assertEquals("#/$defs/X", out.get("$ref"));
        assertNull(out.get("type"));
    }

    @Test
    void schemaHeaderKeptWhenPresent() {
        final var out = service.toJsonSchema202012(Map.of("$schema", "https://example.org/custom", "type", "string"));
        assertEquals("https://example.org/custom", out.get("$schema"));
    }

    @Test
    void definitionsIsRenamedToDefsAndRefsRelocated() {
        final Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.ofEntries(entry("a", Map.<String, Object>of("$ref", "#/definitions/A", "nullable", true))),
                "definitions", Map.of("A", Map.<String, Object>of("type", "string", "nullable", true)));
        final var out = service.toJsonSchema202012(schema);
        // the container became $defs and its schema converted
        assertEquals(Map.of("type", List.of("string", "null")), ((Map<String, Object>) out.get("$defs")).get("A"));
        // the reference was relocated
        final var a = (Map<String, Object>) ((Map<String, Object>) out.get("properties")).get("a");
        assertEquals("#/$defs/A", a.get("$ref"));
    }

    @Test
    void schemasContainerIsRenamedToDefsAndRefsRelocated() {
        final var out = service.toJsonSchema202012(Map.of(
                "type", "object",
                "schemas", Map.of("com.example.Age", Map.of("type", "integer", "nullable", true)),
                "properties", Map.of("age", Map.of("$ref", "#/schemas/com.example.Age", "nullable", true))));
        final var age = (Map<String, Object>) ((Map<String, Object>) out.get("properties")).get("age");
        assertEquals("#/$defs/com.example.Age", age.get("$ref"));
        final var defs = (Map<String, Object>) out.get("$defs");
        assertEquals(Map.of("type", List.of("integer", "null")), defs.get("com.example.Age"));
    }

    @Test
    void fullGeneratedModelDocument() {
        final var out = service.toJsonSchema202012(Map.of(
                "$id", "com.example.Person",
                "type", "object",
                "title", "Person",
                "properties", Map.of(
                        "name", Map.of("type", "string", "nullable", true, "description", "the name"),
                        "count", Map.of("type", "integer", "format", "int32", "nullable", true),
                        "amount", Map.of("type", "number", "nullable", false),
                        "birthday", Map.of("type", "string", "format", "date", "nullable", true),
                        "boss", Map.of("$ref", "#/schemas/com.example.Person", "nullable", true),
                        "children", Map.of("type", "array", "items", Map.of("$ref", "#/schemas/com.example.Person", "nullable", true)),
                        "tags", Map.of("type", "object", "additionalProperties", Map.of("type", "string", "nullable", true)),
                        "status", Map.of("type", "string", "enum", List.of("ON", "OFF")),
                        "extra", Map.of("type", "object", "nullable", true, "additionalProperties", true))));

        assertEquals(SCHEMA_2020_12, out("$schema", out));
        assertEquals("com.example.Person", out("$id", out));
        assertEquals("Person", out("title", out));

        final var props = (Map<String, Object>) out("properties", out);
        assertEquals(List.of("string", "null"), leaf(props, "name").get("type"));
        assertEquals("the name", leaf(props, "name").get("description"));
        assertEquals(List.of("integer", "null"), leaf(props, "count").get("type"));
        assertEquals("int32", leaf(props, "count").get("format"));
        assertEquals("number", leaf(props, "amount").get("type"));
        assertEquals(List.of("string", "null"), leaf(props, "birthday").get("type"));
        assertEquals("date", leaf(props, "birthday").get("format"));
        assertEquals("#/$defs/com.example.Person", leaf(props, "boss").get("$ref"));
        assertNull(leaf(props, "boss").get("type"));
        final var items = (Map<String, Object>) leaf(props, "children").get("items");
        assertEquals("#/$defs/com.example.Person", items.get("$ref"));
        final var tags = (Map<String, Object>) leaf(props, "tags").get("additionalProperties");
        assertEquals(List.of("string", "null"), tags.get("type"));
        assertEquals(List.of("ON", "OFF"), leaf(props, "status").get("enum"));
        // nullable: true + additionalProperties keeps the type union on the object node
        assertEquals(List.of("object", "null"), leaf(props, "extra").get("type"));
    }

    @Test
    void enumConstAndDefaultArePreserved() {
        final var out = service.toJsonSchema202012(Map.of(
                "type", "object",
                "properties", Map.of(
                        "enum1", Map.of("type", "string", "enum", List.of("a", "b"), "nullable", true),
                        "constant", Map.of("type", "string", "const", "fixed", "nullable", true),
                        "defaulted", Map.of("type", "number", "default", new BigDecimal("12.5"), "nullable", true))));
        final var props = (Map<String, Object>) out.get("properties");
        assertEquals(List.of("a", "b"), leaf(props, "enum1").get("enum"));
        assertEquals(List.of("string", "null"), leaf(props, "enum1").get("type"));
        assertEquals("fixed", leaf(props, "constant").get("const"));
        assertEquals(List.of("string", "null"), leaf(props, "constant").get("type"));
        assertEquals(List.of("number", "null"), leaf(props, "defaulted").get("type"));
        assertEquals(new BigDecimal("12.5"), leaf(props, "defaulted").get("default"));
    }

    @Test
    void externalRefsAreNotRelocated() {
        final var out = service.toJsonSchema202012(Map.of(
                "$ref", "https://example.org/common.json#/$defs/Thing",
                "properties", Map.of("a", Map.of("$ref", "other.json#/definitions/X"))));
        assertEquals("https://example.org/common.json#/$defs/Thing", out.get("$ref"));
        final var props = (Map<String, Object>) out.get("properties");
        assertEquals("other.json#/definitions/X", leaf(props, "a").get("$ref"));
    }

    @Test
    void rootNullableCollapsesToNull() {
        assertEquals(Map.of("$schema", SCHEMA_2020_12, "type", "null"), service.toJsonSchema202012(Map.of("nullable", true)));
    }

    @Test
    void mapOverloadMatchesObjectOverload() {
        final var raw = Map.of("type", "object", "properties", Map.of("id", Map.of("type", "string", "nullable", true)));
        assertEquals(service.toJsonSchema202012((Object) raw), service.toJsonSchema202012(raw));
    }

    @Test
    void nullInputYieldsNull() {
        assertNull(service.toJsonSchema202012((Object) null));
    }

    @Test
    void typedSingleSchemaConversion() {
        final var schema = new RawBuildJsonSchema(
                "com.example.Person", null, "object", null, null, null, null,
                Map.of("name", new RawBuildJsonSchema(null, null, "string", true, null, null, null, null, null, null, null, null, null)),
                null, "Person", null, null, null);
        final var out = service.toJsonSchema202012(schema);
        assertEquals(SCHEMA_2020_12, out("$schema", out));
        final var props = (Map<String, Object>) out("properties", out);
        assertEquals(List.of("string", "null"), ((Map<String, Object>) props.get("name")).get("type"));
    }

    @Test
    void typedBundleReordersRefsIntoDefs() {
        final var age = new RawBuildJsonSchema("com.example.Age", null, "integer", true, null, null, null, null, null, null, null, null, null);
        final var person = new RawBuildJsonSchema(
                "com.example.Person", null, "object", null, null, null, null,
                Map.of("age", new RawBuildJsonSchema(null, "#/schemas/com.example.Age", null, null, null, null, null, null, null, null, null, null, null)),
                null, "Person", null, null, null);
        final var out = service.toJsonSchema202012(List.of(person, age));
        final var defs = (Map<String, Object>) out.get("$defs");
        assertEquals(SCHEMA_2020_12, out.get("$schema"));
        assertEquals(Map.of("type", List.of("integer", "null")), propsOf(defs, "com.example.Age"));
        final var personProps = (Map<String, Object>) propsOf(defs, "com.example.Person").get("properties");
        final var ageRef = (Map<String, Object>) ((Map<String, Object>) personProps.get("age"));
        assertEquals("#/$defs/com.example.Age", ageRef.get("$ref"));
    }

    @Test
    void bundleTreeDeserialization() {
        final Map<String, Object> bundle = Map.of("schemas", Map.of(
                "com.example.Person", Map.<String, Object>of("$id", "com.example.Person", "type", "object", "properties",
                        Map.of("name", Map.<String, Object>of("type", "string", "nullable", true))),
                "com.example.Age", Map.<String, Object>of("$id", "com.example.Age", "type", "integer", "nullable", true)));
        final var out = service.to2020Bundle(bundle);
        final var defs = (Map<String, Object>) out.get("$defs");
        assertEquals(SCHEMA_2020_12, out.get("$schema"));
        final var personProps = (Map<String, Object>) propsOf(defs, "com.example.Person").get("properties");
        assertEquals(List.of("string", "null"), leaf(personProps, "name").get("type"));
        assertEquals(Map.of("type", List.of("integer", "null")), propsOf(defs, "com.example.Age"));
    }

    @Test
    void typedSchemasExtractionPreservesBundleKeysAsIds() {
        final Map<String, Object> bundle = Map.of("schemas", Map.of(
                "RootNamed", Map.<String, Object>of("type", "string", "nullable", true)));
        final var typed = service.toTypedSchemas(bundle);
        assertEquals(1, typed.size());
        assertEquals("RootNamed", typed.get(0).id());
        assertEquals(List.of("string", "null"), ((Map<String, Object>) service.toJsonSchema202012(typed.get(0))).get("type"));
    }

    @Test
    void fromJsonTreeRecursivelyBuildsNestedSchemas() {
        final var tree = new LinkedHashMap<String, Object>(Map.of(
                "$id", "com.example.Outer", "type", "object", "nullable", true,
                "properties", Map.of("child", Map.of("$ref", "#/schemas/com.example.Child", "nullable", true)),
                "items", Map.of("type", "string")));
        final var typed = RawBuildJsonSchema.fromJsonTree(tree);
        assertEquals("com.example.Outer", typed.id());
        assertEquals(Boolean.TRUE, typed.nullable());
        assertEquals("object", typed.type());
        assertEquals("#/schemas/com.example.Child", typed.properties().get("child").ref());
        assertEquals("string", typed.items().type());
    }

    @Test
    void typedSchemaRoundTripThroughMapper() {
        final var configuration = new ConfigurationImpl(List.of(new MapConfigSource(Map.of())));
        try (final var mapper = new JsonMapperImpl(List.of(new RawBuildJsonSchemaJsonCodec()), configuration)) {
            final var schema = new RawBuildJsonSchema(
                    "com.example.Person", "#/schemas/com.example.Age", "object", true, "x-format", "p.*",
                    Boolean.TRUE,
                    Map.of("name", new RawBuildJsonSchema(null, null, "string", true, null, null,
                            null, null, null, "the name", null, null, List.of("a"))),
                    new RawBuildJsonSchema(null, null, "array", null, null, null, null, null,
                            new RawBuildJsonSchema(null, null, "integer", null, "int32", null, null, null, null, null, null, null, null),
                            null, null, null, List.of("required-a")),
                    "Person", "a person", List.of("ON", "OFF"), null);

            final var json = mapper.toString(schema);
            final var readBack = mapper.fromString(RawBuildJsonSchema.class, json);
            assertEquals(schema, readBack);
            assertEquals("com.example.Person", readBack.id());
            assertEquals("#/schemas/com.example.Age", readBack.ref());
            assertEquals(Boolean.TRUE, readBack.additionalProperties());
            assertEquals("string", readBack.properties().get("name").type());
            assertEquals(Boolean.TRUE, readBack.properties().get("name").nullable());
            assertEquals(List.of("a"), readBack.properties().get("name").required());
            assertEquals("array", readBack.items().type());
            assertEquals("int32", readBack.items().items().format());
            assertEquals(List.of("ON", "OFF"), readBack.enumeration());
            assertEquals(List.of("required-a"), readBack.items().required());
        }
    }

    @Test
    void fromJsonTreeWithoutMapReturnsNull() {
        assertNull(RawBuildJsonSchema.fromJsonTree(null));
        assertNull(RawBuildJsonSchema.fromJsonTree("not-a-map"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> leaf(final Map<String, Object> props, final String key) {
        return (Map<String, Object>) props.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> propsOf(final Map<String, Object> defs, final String key) {
        return (Map<String, Object>) defs.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Object out(final String key, final Map<String, Object> map) {
        return map.get(key);
    }
}