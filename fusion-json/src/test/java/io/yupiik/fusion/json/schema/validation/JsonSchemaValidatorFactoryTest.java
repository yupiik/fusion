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
package io.yupiik.fusion.json.schema.validation;

import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.pretty.PrettyJsonMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class JsonSchemaValidatorFactoryTest {
    private static JsonSchemaValidatorFactory FACTORY;
    private static final Factory INSTANCE_FACTORY = new Factory();

    @BeforeAll
    static void init() {
        FACTORY = new JsonSchemaValidatorFactory();
    }

    @AfterAll
    static void destroy() {
        FACTORY.close();
    }

    @Test
    void patternPropertiesNested() {
        try (final var validator = FACTORY.newInstance(Map.of(
                "type", "object",
                "properties", Map.of(
                        "nested", Map.of(
                                "type", "object",
                                "patternProperties", Map.of(
                                        "[0-9]+", Map.of("type", "number"))))))) {

            assertTrue(validator.apply(Map.of()).isSuccess());
            assertTrue(validator.apply(Map.of("nested", Map.of("1", 1))).isSuccess());

            final var result = validator.apply(Map.of("nested", Map.of("1", "test")));
            assertFalse(result.isSuccess(), result::toString);
        }
    }


    @Test
    void rootRequired() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "string")
                                .build())
                        .add("age", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "number")
                                .build())
                        .build())
                .add("required", INSTANCE_FACTORY.createArrayBuilder().add("name").build())
                .build());

        final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "ok").build());
        assertTrue(success.isSuccess(), success.errors()::toString);

        final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder().addNull("name").build());
        assertFalse(failure.isSuccess());
        final var errors = failure.errors();
        assertEquals(1, errors.size());
        final var error = errors.iterator().next();
        assertEquals("/", error.field());
        assertEquals("name is required and is not present", error.message());

        validator.close();
    }

    @Test
    void rootType() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "string")
                                .build())
                        .add("age", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "number")
                                .build())
                        .build())
                .build());

        {
            final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "ok").build());
            assertTrue(success.isSuccess(), success.errors()::toString);
        }
        {
            final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder().addNull("name").build());
            assertTrue(success.isSuccess(), success.errors()::toString);
        }

        final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", 5).build());
        assertFalse(failure.isSuccess());
        final var errors = failure.errors();
        assertEquals(1, errors.size());
        final var error = errors.iterator().next();
        assertEquals("/name", error.field());
        assertEquals("Expected [NULL, STRING] but got java.lang.Integer", error.message());

        validator.close();
    }

    @Test
    void typeArray() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", INSTANCE_FACTORY.createArrayBuilder()
                                        .add("string")
                                        .add("number"))
                                .build())
                        .build())
                .build());

        {
            final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "ok").build());
            assertTrue(success.isSuccess(), success.errors()::toString);
        }
        {
            final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder().addNull("name").build());
            assertTrue(success.isSuccess(), success.errors()::toString);
        }
        {
            final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", 5).build());
            assertTrue(success.isSuccess(), success.errors()::toString);
        }

        final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", true).build());
        assertFalse(failure.isSuccess());
        final var errors = failure.errors();
        assertEquals(1, errors.size());
        final var error = errors.iterator().next();
        assertEquals("/name", error.field());
        assertEquals("Expected [NULL, NUMBER, STRING] but got java.lang.Boolean", error.message());

        validator.close();
    }

    @Test
    void nestedType() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("person", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "object")
                                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                                .add("type", "string")
                                                .build())
                                        .add("age", INSTANCE_FACTORY.createObjectBuilder()
                                                .add("type", "number")
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build());

        final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("person", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", "ok")
                        .build())
                .build());
        assertTrue(success.isSuccess(), success.errors()::toString);

        final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("person", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder().build())
                        .build())
                .build());
        assertFalse(failure.isSuccess(), failure::toString);
        final var errors = failure.errors();
        assertEquals(1, errors.size());
        final var error = errors.iterator().next();
        assertEquals("/person/name", error.field());
        assertEquals("Expected [NULL, STRING] but got java.util.HashMap", error.message());

        validator.close();
    }

    @Test
    void enumValues() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "string")
                                .add("enum", INSTANCE_FACTORY.createArrayBuilder().add("a").add("b").build())
                                .build())
                        .build())
                .build());

        final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "a").build());
        assertTrue(success.isSuccess(), success.errors()::toString);

        final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", 5).build());
        assertFalse(failure.isSuccess());
        final var errors = failure.errors();
        assertEquals(2, errors.size());
        final var error = errors.iterator().next();
        assertEquals("/name", error.field());
        assertEquals("Expected [NULL, STRING] but got java.lang.Integer", error.message());

        validator.close();
    }

    @Test
    void multipleOf() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("age", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "number")
                                .add("multipleOf", 5)
                                .build())
                        .build())
                .build());

        final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", 5).build());
        assertTrue(success.isSuccess(), success.errors()::toString);

        final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", 6).build());
        assertFalse(failure.isSuccess());
        final var errors = failure.errors();
        assertEquals(1, errors.size());
        final var error = errors.iterator().next();
        assertEquals("/age", error.field());
        assertEquals("6.0 is not a multiple of 5.0", error.message());

        validator.close();
    }

    @Test
    void minimum() {
        {
            final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                    .add("type", "object")
                    .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                            .add("age", INSTANCE_FACTORY.createObjectBuilder()
                                    .add("type", "number")
                                    .add("minimum", 5)
                                    .build())
                            .build())
                    .build());

            assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", 5).build()).isSuccess());
            assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", 6).build()).isSuccess());

            final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", 2).build());
            assertFalse(failure.isSuccess());
            final var errors = failure.errors();
            assertEquals(1, errors.size());
            final var error = errors.iterator().next();
            assertEquals("/age", error.field());
            assertEquals("2.0 is less than 5.0", error.message());

            validator.close();
        }
        {
            final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                    .add("type", "object")
                    .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                            .add("age", INSTANCE_FACTORY.createObjectBuilder()
                                    .add("type", "number")
                                    .add("minimum", -1)
                                    .build())
                            .build())
                    .build());

            assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", 1).build()).isSuccess());
            assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", 0).build()).isSuccess());
            assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", -1).build()).isSuccess());

            final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", -2).build());
            assertFalse(failure.isSuccess());
            final var errors = failure.errors();
            assertEquals(1, errors.size());
            final var error = errors.iterator().next();
            assertEquals("/age", error.field());
            assertEquals("-2.0 is less than -1.0", error.message());

            validator.close();
        }
    }

    @Test
    void maximum() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("age", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "number")
                                .add("maximum", 5)
                                .build())
                        .build())
                .build());

        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", 5).build()).isSuccess());
        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", 4).build()).isSuccess());

        final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", 6).build());
        assertFalse(failure.isSuccess());
        final var errors = failure.errors();
        assertEquals(1, errors.size());
        final var error = errors.iterator().next();
        assertEquals("/age", error.field());
        assertEquals("6.0 is more than 5.0", error.message());

        validator.close();
    }

    @Test
    void exclusiveMinimum() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("age", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "number")
                                .add("exclusiveMinimum", 5)
                                .build())
                        .build())
                .build());

        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", 6).build()).isSuccess());
        assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", 5).build()).isSuccess());
        assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", 4).build()).isSuccess());
        validator.close();
    }

    @Test
    void exclusiveMaximum() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("age", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "number")
                                .add("exclusiveMaximum", 5)
                                .build())
                        .build())
                .build());

        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", 4).build()).isSuccess());
        assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", 5).build()).isSuccess());
        assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", 6).build()).isSuccess());

        validator.close();
    }

    @Test
    void integerType() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("age", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "integer")
                                .build())
                        .build())
                .build());

        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder().build()).isSuccess());
        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", 30).build()).isSuccess());
        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", -10).build()).isSuccess());
        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", BigInteger.valueOf(50)).build()).isSuccess());
        // check no decimal numbers allowed
        assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", 30.3f).build()).isSuccess());
        assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", -7.4d).build()).isSuccess());
        assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("age", BigDecimal.valueOf(50.35613d)).build()).isSuccess());

        validator.close();
    }

    @Test
    void minLength() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "string")
                                .add("minLength", 2)
                                .build())
                        .build())
                .build());

        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "ok").build()).isSuccess());
        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "okk").build()).isSuccess());
        assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "-").build()).isSuccess());

        validator.close();
    }

    @Test
    void maxLength() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "string")
                                .add("maxLength", 2)
                                .build())
                        .build())
                .build());

        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "ok").build()).isSuccess());
        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "-").build()).isSuccess());
        assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "fail").build()).isSuccess());

        validator.close();
    }

    @Test
    void pattern() {
        try (final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "string")
                                .add("pattern", "[a-z]")
                                .build())
                        .build())
                .build())) {
            final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "ok").build());
            assertTrue(success.isSuccess(), success::toString);
            assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "-").build()).isSuccess());
            assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "0").build()).isSuccess());
        }
    }

    @TestFactory
    Stream<DynamicTest> patternFull() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "string")
                                // from https://json-schema.org/understanding-json-schema/reference/regular_expressions
                                .add("pattern", "^(\\([0-9]{3}\\))?[0-9]{3}-[0-9]{4}$")
                                .build())
                        .build())
                .build());
        final BiConsumer<String, Boolean> test = (value, result) -> {
            final var res = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", value).build());
            assertEquals(result, res.isSuccess(), () -> "Error: " + res.errors() + "\nValue: " + value);
        };
        return Stream.of(
                        dynamicTest("patternFull_ok[555-1212]", () -> test.accept("555-1212", true)),
                        dynamicTest("patternFull_ok_[(888)555-1212]", () -> test.accept("(888)555-1212", true)),
                        dynamicTest("patternFull_ok_[(888)555-1212 ext. 532]", () -> test.accept("(888)555-1212 ext. 532", false)),
                        dynamicTest("patternFull_ok_[(800)FLOWERS]", () -> test.accept("(800)FLOWERS", false)))
                .onClose(validator::close);
    }

    @Test
    void itemsObject() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("names", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "array")
                                .add("items", INSTANCE_FACTORY.createObjectBuilder()
                                        .add("type", "string"))
                                .build())
                        .build())
                .build());

        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("names", INSTANCE_FACTORY.createArrayBuilder().add("1")).build()).isSuccess());
        assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("names", INSTANCE_FACTORY.createArrayBuilder().add(1)).build()).isSuccess());

        validator.close();
    }

    @Test
    void itemsArray() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("names", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "array")
                                .add("items", INSTANCE_FACTORY.createArrayBuilder().add(INSTANCE_FACTORY.createObjectBuilder()
                                                .add("type", "string"))
                                        .build()).build())
                        .build())
                .build());

        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("names", INSTANCE_FACTORY.createArrayBuilder().add("1")).build()).isSuccess());
        assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("names", INSTANCE_FACTORY.createArrayBuilder().add(1)).build()).isSuccess());

        validator.close();
    }

    @Test
    void itemsValidatesObject() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("names", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "array")
                                .add("items", INSTANCE_FACTORY.createObjectBuilder()
                                        .add("type", "object")
                                        .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                                                .add("age", INSTANCE_FACTORY.createObjectBuilder()
                                                        .add("type", "number")
                                                        .add("maximum", 2)
                                                        .build())
                                                .build()))
                                .build())
                        .build())
                .build());

        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("names", INSTANCE_FACTORY.createArrayBuilder()
                        .add(INSTANCE_FACTORY.createObjectBuilder().add("age", 2)))
                .build()).isSuccess());
        assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("names", INSTANCE_FACTORY.createArrayBuilder()
                        .add(INSTANCE_FACTORY.createArrayBuilder().build()))
                .build()).isSuccess());
        assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("names", INSTANCE_FACTORY.createArrayBuilder()
                        .add(INSTANCE_FACTORY.createObjectBuilder().add("age", 3)))
                .build()).isSuccess());

        validator.close();
    }

    @Test
    void maxItems() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("names", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "array")
                                .add("maxItems", 1)
                                .build())
                        .build())
                .build());

        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("names", INSTANCE_FACTORY.createArrayBuilder().add(2))
                .build()).isSuccess());
        assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("names", INSTANCE_FACTORY.createArrayBuilder().add(2).add(3))
                .build()).isSuccess());

        validator.close();
    }

    @Test
    void minItems() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("names", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "array")
                                .add("minItems", 1)
                                .build())
                        .build())
                .build());

        final var result = validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("names", INSTANCE_FACTORY.createArrayBuilder().add(2))
                .build());
        assertTrue(result.isSuccess(), result::toString);
        assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("names", INSTANCE_FACTORY.createArrayBuilder())
                .build()).isSuccess());

        validator.close();
    }

    @Test
    void uniqueItems() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("names", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "array")
                                .add("uniqueItems", true)
                                .build())
                        .build())
                .build());

        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("names", INSTANCE_FACTORY.createArrayBuilder().add(2))
                .build()).isSuccess());
        assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("names", INSTANCE_FACTORY.createArrayBuilder().add(2).add(2))
                .build()).isSuccess());

        validator.close();
    }

    @Test
    void containsItems() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("names", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "array")
                                .add("contains", INSTANCE_FACTORY.createObjectBuilder().add("type", "number"))
                                .build())
                        .build())
                .build());

        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("names", INSTANCE_FACTORY.createArrayBuilder().add(2))
                .build()).isSuccess());
        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("names", INSTANCE_FACTORY.createArrayBuilder().add(2).add("test"))
                .build()).isSuccess());
        assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("names", INSTANCE_FACTORY.createArrayBuilder().add("test"))
                .build()).isSuccess());

        validator.close();
    }

    @Test
    void maxProperties() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("maxProperties", 1)
                .build());

        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder().build()).isSuccess());
        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("name", "test")
                .build()).isSuccess());
        assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("name", "test")
                .add("name2", "test")
                .build()).isSuccess());

        validator.close();
    }

    @Test
    void minProperties() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("minProperties", 1)
                .build());

        assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder().build()).isSuccess());
        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("name", "test")
                .build()).isSuccess());
        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("name", "test")
                .add("name2", "test")
                .build()).isSuccess());

        validator.close();
    }

    @Test
    void patternProperties() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("patternProperties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("[0-9]+", INSTANCE_FACTORY.createObjectBuilder().add("type", "number"))
                        .build())
                .build());

        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder().build()).isSuccess());
        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("1", 1)
                .build()).isSuccess());
        assertFalse(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("1", "test")
                .build()).isSuccess());

        validator.close();
    }

    @Test
    void additionalProperties() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("additionalProperties", INSTANCE_FACTORY.createObjectBuilder().add("type", "number"))
                .build());

        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder().build()).isSuccess());
        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("1", 1)
                .build()).isSuccess());
        assertTrue(validator.apply(INSTANCE_FACTORY.createObjectBuilder()
                .add("1", "test")
                .build()).isSuccess());

        validator.close();
    }

    @Test
    void dateTimeFormatValues() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "string")
                                .add("format", "date-time")
                                .build())
                        .build())
                .build());

        final var success_with_tz = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "2007-12-03T10:15:30.00Z").build());
        assertTrue(success_with_tz.isSuccess(), success_with_tz.errors()::toString);

        final var success_with_offset = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "2018-11-13T20:20:39+00:00").build());
        assertTrue(success_with_offset.isSuccess(), success_with_offset.errors()::toString);

        final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "a").build());
        assertFalse(failure.isSuccess());
        final var errors = failure.errors();
        assertEquals(1, errors.size());
        final var error = errors.iterator().next();
        assertEquals("/name", error.field());
        assertEquals("a is not a DateTime format", error.message());

        validator.close();
    }

    @Test
    void dateFormatValues() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "string")
                                .add("format", "date")
                                .build())
                        .build())
                .build());

        final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "2023-01-26").build());
        assertTrue(success.isSuccess(), success.errors()::toString);

        final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "a").build());
        assertFalse(failure.isSuccess());
        final var errors = failure.errors();
        assertEquals(1, errors.size());
        final var error = errors.iterator().next();
        assertEquals("/name", error.field());
        assertEquals("a is not a Date format", error.message());

        validator.close();
    }

    @Test
    void timeFormatValues() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "string")
                                .add("format", "time")
                                .build())
                        .build())
                .build());

        final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "20:20:39+00:00").build());
        assertTrue(success.isSuccess(), success.errors()::toString);

        final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "a").build());
        assertFalse(failure.isSuccess());
        final var errors = failure.errors();
        assertEquals(1, errors.size());
        final var error = errors.iterator().next();
        assertEquals("/name", error.field());
        assertEquals("a is not a Time format", error.message());

        validator.close();
    }

    @Test
    void durationFormatValues() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "string")
                                .add("format", "duration")
                                .build())
                        .build())
                .build());

        final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "P3D").build());
        assertTrue(success.isSuccess(), success.errors()::toString);

        final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "a").build());
        assertFalse(failure.isSuccess());
        final var errors = failure.errors();
        assertEquals(1, errors.size());
        final var error = errors.iterator().next();
        assertEquals("/name", error.field());
        assertEquals("a is not a Duration format", error.message());

        validator.close();
    }

    @Test
    void emailFormatValues() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "string")
                                .add("format", "email")
                                .build())
                        .build())
                .build());

        final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "obiwan.kenobi@jedi.org").build());
        assertTrue(success.isSuccess(), success.errors()::toString);

        final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "a.org").build());
        assertFalse(failure.isSuccess());
        final var errors = failure.errors();
        assertEquals(1, errors.size());
        final var error = errors.iterator().next();
        assertEquals("/name", error.field());
        assertEquals("a.org is not an Email format", error.message());

        final var failure2 = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "a@test").build());
        assertFalse(failure2.isSuccess());
        final var errors2 = failure2.errors();
        assertEquals(1, errors2.size());
        final var error2 = errors2.iterator().next();
        assertEquals("/name", error2.field());
        assertEquals("a@test is not an Email format", error2.message());

        validator.close();
    }

    @Test
    void hostnameFormatValues() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "string")
                                .add("format", "hostname")
                                .build())
                        .build())
                .build());

        Stream.of(
                "www.yupiik.io",
                "oss.yupiik.io").forEach(
                hostname -> {
                    final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", hostname).build());
                    assertTrue(success.isSuccess(), success.errors()::toString);
                }
        );

        Stream.of(
                ".org",
                "#@$test .tst.not").forEach(
                hostname -> {
                    final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", ".org").build());
                    assertFalse(failure.isSuccess());
                    final var errors = failure.errors();
                    assertEquals(1, errors.size());
                    final var error = errors.iterator().next();
                    assertEquals("/name", error.field());
                    assertEquals(".org is not a Hostname format", error.message());
                }
        );



        final var failure2 = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "#@$test .tst.not").build());
        assertFalse(failure2.isSuccess());
        final var errors2 = failure2.errors();
        assertEquals(1, errors2.size());
        final var error2 = errors2.iterator().next();
        assertEquals("/name", error2.field());
        assertEquals("#@$test .tst.not is not a Hostname format", error2.message());

        validator.close();
    }

    @Test
    void ipv4FormatValues() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "string")
                                .add("format", "ipv4")
                                .build())
                        .build())
                .build());

        final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "127.0.0.1").build());
        assertTrue(success.isSuccess(), success.errors()::toString);

        final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "a").build());
        assertFalse(failure.isSuccess());
        final var errors = failure.errors();
        assertEquals(1, errors.size());
        final var error = errors.iterator().next();
        assertEquals("/name", error.field());
        assertEquals("a is not a IPv4 format", error.message());

        validator.close();
    }

    @Test
    void ipv6FormatValues() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "string")
                                .add("format", "ipv6")
                                .build())
                        .build())
                .build());

        final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "001:0db8:0000:85a3:0000:0000:ac1f:8001").build());
        assertTrue(success.isSuccess(), success.errors()::toString);

        final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "a").build());
        assertFalse(failure.isSuccess());
        final var errors = failure.errors();
        assertEquals(1, errors.size());
        final var error = errors.iterator().next();
        assertEquals("/name", error.field());
        assertEquals("a is not a IPv6 format", error.message());

        validator.close();
    }

    @Test
    void uuidFormatValues() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "string")
                                .add("format", "uuid")
                                .build())
                        .build())
                .build());

        final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "3e4666bf-d5e5-4aa7-b8ce-cefe41c7568a").build());
        assertTrue(success.isSuccess(), success.errors()::toString);

        final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "a").build());
        assertFalse(failure.isSuccess());
        final var errors = failure.errors();
        assertEquals(1, errors.size());
        final var error = errors.iterator().next();
        assertEquals("/name", error.field());
        assertEquals("a is not a UUID format", error.message());

        validator.close();
    }

    @Test
    void uriFormatValues() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "string")
                                .add("format", "uri")
                                .build())
                        .build())
                .build());

        Stream.of(
                "http://www.yupiik.io/fusion/",
                "https://www.yupiik.io/fusion/",
                "https://user:password@www.yupiik.io/fusion/index.html#anchor",
                "ftp://user:password@www.yupiik.io/fusion/",
                "sftp://www.yupiik.io/fusion/",
                "file://fusion").forEach(
                        uri -> {
                            final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", uri).build());
                            assertTrue(success.isSuccess(), success.errors()::toString);
                        }
        );

        final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "http:context/nowhere.com").build());
        assertFalse(failure.isSuccess());
        final var errors = failure.errors();
        assertEquals(1, errors.size());
        final var error = errors.iterator().next();
        assertEquals("/name", error.field());
        assertEquals("http:context/nowhere.com is not a Uri format", error.message());

        validator.close();
    }

    @Test
    void regexFormatValues() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "string")
                                .add("format", "regex")
                                .build())
                        .build())
                .build());

        final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "^(\\([0-9]{3}\\))?[0-9]{3}-[0-9]{4}$").build());
        assertTrue(success.isSuccess(), success.errors()::toString);

        final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "^(a$").build());
        assertFalse(failure.isSuccess());
        final var errors = failure.errors();
        assertEquals(1, errors.size());
        final var error = errors.iterator().next();
        assertEquals("/name", error.field());
        assertEquals("^(a$ is not a Regex format", error.message());

        validator.close();
    }

    @Test
    void jsonPointerFormatValues() {
        final var validator = FACTORY.newInstance(INSTANCE_FACTORY.createObjectBuilder()
                .add("type", "object")
                .add("properties", INSTANCE_FACTORY.createObjectBuilder()
                        .add("name", INSTANCE_FACTORY.createObjectBuilder()
                                .add("type", "string")
                                .add("format", "json-pointer")
                                .build())
                        .build())
                .build());

        final var success = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "/customer.id").build());
        assertTrue(success.isSuccess(), success.errors()::toString);

        final var failure = validator.apply(INSTANCE_FACTORY.createObjectBuilder().add("name", "a").build());
        assertFalse(failure.isSuccess());
        final var errors = failure.errors();
        assertEquals(1, errors.size());
        final var error = errors.iterator().next();
        assertEquals("/name", error.field());
        assertEquals("a is not a JsonPointer format", error.message());

        validator.close();
    }

    /*
    @Test
    void validateFile() {
        @Injection
        JsonMapper jsonMapper;

        final var schema = jsonMapper.fromString(Object.class,
                """
                        {
                          "$id": "https://spec.openapis.org/oas/3.1/schema/2022-10-07",
                          "$schema": "https://json-schema.org/draft/2020-12/schema",
                          "description": "The description of OpenAPI v3.1.x documents without schema validation, as defined by https://spec.openapis.org/oas/v3.1.0",
                          "type": "object"
                          ...
                        }
                      """);
        final JsonSchemaValidatorFactory factory = new JsonSchemaValidatorFactory();
        final JsonSchemaValidator validator = factory.newInstance((Map<String, Object>) schema);
        validator.apply();

        validator.close();
    }
    */


    private static class Factory { // bridge to not rewrite all johnzon's tests
        private MapBuilder createObjectBuilder() {
            return new MapBuilder();
        }

        private ArrayBuilder createArrayBuilder() {
            return new ArrayBuilder();
        }
    }

    private static class ArrayBuilder {
        private final List<Object> list = new ArrayList<>();

        private ArrayBuilder add(final Object value) {
            list.add(value instanceof MapBuilder mb ? mb.build() : (value instanceof ArrayBuilder a ? a.build() : value));
            return this;
        }

        private List<Object> build() {
            return list;
        }
    }

    private static class MapBuilder {
        private final Map<String, Object> map = new HashMap<>();

        private MapBuilder add(final String key, final Object value) {
            map.put(key, value instanceof MapBuilder mb ? mb.build() : (value instanceof ArrayBuilder a ? a.build() : value));
            return this;
        }

        public MapBuilder addNull(final String name) {
            return add(name, null);
        }

        private Map<String, Object> build() {
            return map;
        }
    }
}
