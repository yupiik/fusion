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

import io.yupiik.fusion.json.internal.codec.BaseJsonCodec;
import io.yupiik.fusion.json.serialization.JsonCodec;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

/**
 * Hand-written, reflectionless {@link JsonCodec} for {@link RawBuildJsonSchema}. It mirrors the codec the Fusion
 * annotation processor would generate for a {@code @JsonModel} record named {@code RawBuildJsonSchema}, keeping it
 * available in the runtime module. It can be registered with a {@link io.yupiik.fusion.json.JsonMapper} to
 * deserialize {@code META-INF/fusion/json/schemas.json} bundles without any reflection.
 */
public class RawBuildJsonSchemaJsonCodec extends BaseJsonCodec<RawBuildJsonSchema> {
    // keys sorted by length so matchString can use the key length as a first discriminator
    private static final char[][] KEYS__ = {
            "$id".toCharArray(),                                             // 3
            "$ref".toCharArray(), "type".toCharArray(), "enum".toCharArray(), // 4
            "title".toCharArray(), "items".toCharArray(),                    // 5
            "format".toCharArray(),                                          // 6
            "pattern".toCharArray(),                                         // 7
            "nullable".toCharArray(), "required".toCharArray(),              // 8
            "properties".toCharArray(),                                      // 10
            "description".toCharArray(),                                     // 11
            "additionalProperties".toCharArray()                             // 20
    };

    private static final IntUnaryOperator KEYS_OFFSETS = length -> switch (length) {
        case 3 -> 0;
        case 4 -> 1;
        case 5 -> 4;
        case 6 -> 6;
        case 7 -> 7;
        case 8 -> 8;
        case 10 -> 10;
        case 11 -> 11;
        case 20 -> 12;
        default -> -1;
    };

    private static RawBuildJsonSchema createFromSlots(final Object[] args) {
        return new RawBuildJsonSchema(
                (String) args[0],
                (String) args[1],
                (String) args[2],
                (Boolean) args[3],
                (String) args[4],
                (String) args[5],
                args[6],
                (Map<String, RawBuildJsonSchema>) args[7],
                (RawBuildJsonSchema) args[8],
                (String) args[9],
                (String) args[10],
                (java.util.List<String>) args[11],
                (java.util.List<String>) args[12]);
    }

    private static final Function<Object[], RawBuildJsonSchema> FACTORY = RawBuildJsonSchemaJsonCodec::createFromSlots;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final FieldMeta<RawBuildJsonSchema>[] FIELDS = new FieldMeta[] {
            // ordered to match the KEYS__ length-sorted layout so readObject indexes them consistently
            new FieldMeta<>("$id".toCharArray(), 0, ContainerKind.VALUE, ValueKind.STRING, true, false, null, m -> ((RawBuildJsonSchema) m).id(), 0, "\"$id\":".toCharArray()),
            new FieldMeta<>("$ref".toCharArray(), 1, ContainerKind.VALUE, ValueKind.STRING, true, false, null, m -> ((RawBuildJsonSchema) m).ref(), 0, "\"$ref\":".toCharArray()),
            new FieldMeta<>("type".toCharArray(), 2, ContainerKind.VALUE, ValueKind.STRING, true, false, null, m -> ((RawBuildJsonSchema) m).type(), 0, "\"type\":".toCharArray()),
            new FieldMeta<>("enum".toCharArray(), 11, ContainerKind.LIST, ValueKind.STRING, true, false, String.class, m -> ((RawBuildJsonSchema) m).enumeration(), 0, "\"enum\":".toCharArray()),
            new FieldMeta<>("title".toCharArray(), 9, ContainerKind.VALUE, ValueKind.STRING, true, false, null, m -> ((RawBuildJsonSchema) m).title(), 0, "\"title\":".toCharArray()),
            new FieldMeta<>("items".toCharArray(), 8, ContainerKind.VALUE, ValueKind.MODEL, true, false, RawBuildJsonSchema.class, m -> ((RawBuildJsonSchema) m).items(), 0, "\"items\":".toCharArray()),
            new FieldMeta<>("format".toCharArray(), 4, ContainerKind.VALUE, ValueKind.STRING, true, false, null, m -> ((RawBuildJsonSchema) m).format(), 0, "\"format\":".toCharArray()),
            new FieldMeta<>("pattern".toCharArray(), 5, ContainerKind.VALUE, ValueKind.STRING, true, false, null, m -> ((RawBuildJsonSchema) m).pattern(), 0, "\"pattern\":".toCharArray()),
            new FieldMeta<>("nullable".toCharArray(), 3, ContainerKind.VALUE, ValueKind.BOOLEAN, true, false, null, m -> ((RawBuildJsonSchema) m).nullable(), 0, "\"nullable\":".toCharArray()),
            new FieldMeta<>("required".toCharArray(), 12, ContainerKind.LIST, ValueKind.STRING, true, false, String.class, m -> ((RawBuildJsonSchema) m).required(), 0, "\"required\":".toCharArray()),
            new FieldMeta<>("properties".toCharArray(), 7, ContainerKind.MAP, ValueKind.MODEL, true, false, RawBuildJsonSchema.class, m -> ((RawBuildJsonSchema) m).properties(), 0, "\"properties\":".toCharArray()),
            new FieldMeta<>("description".toCharArray(), 10, ContainerKind.VALUE, ValueKind.STRING, true, false, null, m -> ((RawBuildJsonSchema) m).description(), 0, "\"description\":".toCharArray()),
            new FieldMeta<>("additionalProperties".toCharArray(), 6, ContainerKind.VALUE, ValueKind.GENERIC_OBJECT, true, false, Object.class, m -> ((RawBuildJsonSchema) m).additionalProperties(), 0, "\"additionalProperties\":".toCharArray())
    };

    private static final FieldMeta<RawBuildJsonSchema>[] FIELDS_WRITE = FIELDS;

    public RawBuildJsonSchemaJsonCodec() {
        super(RawBuildJsonSchema.class);
    }

    @Override
    public RawBuildJsonSchema read(final DeserializationContext context) throws IOException {
        return readObject(context, KEYS__, KEYS_OFFSETS, FIELDS, FACTORY);
    }

    @Override
    public void write(final RawBuildJsonSchema instance, final SerializationContext context) throws IOException {
        writeObject(instance, context, FIELDS_WRITE);
    }
}