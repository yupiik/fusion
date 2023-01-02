/*
 * Copyright (c) 2022-2023 - Yupiik SAS - https://www.yupiik.com
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
package io.yupiik.fusion.json.internal.codec;

import io.yupiik.fusion.json.internal.JsonStrings;
import io.yupiik.fusion.json.serialization.JsonCodec;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// used to handle unknown structures
public class ObjectJsonCodec implements JsonCodec<Object> {
    private final CollectionJsonCodec<Object, Collection<Object>> collectionCodec = new CollectionJsonCodec<>(this, List.class, ArrayList::new);
    private final MapJsonCodec<Object> mapCodec = new MapJsonCodec<>(this);

    @Override
    public Type type() {
        return Object.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object read(final DeserializationContext context) throws IOException {
        final var parser = context.parser();
        if (!parser.hasNext()) {
            return null;
        }

        final var next = parser.next();
        return switch (next) {
            case VALUE_NULL -> null;
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            case VALUE_STRING -> parser.getString();
            case VALUE_NUMBER -> parser.getBigDecimal();
            case START_OBJECT -> {
                parser.rewind(next);
                yield mapCodec.read(context);
            }
            case START_ARRAY -> {
                parser.rewind(next);
                yield collectionCodec.read(context);
            }
            default -> throw new IllegalStateException("Invalid event: " + next);
        };
    }

    // note: this only handles the symmetric types of the read() method so booleans, bigdecimals, strings,
    //        arrays of these types and maps of these types
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void write(final Object value, final SerializationContext context) throws IOException {
        final var writer = context.writer();
        if (value == null) {
            writer.write("null");
            return;
        }
        if (value instanceof String s) {
            writer.write(JsonStrings.escape(s));
            return;
        }
        if (value instanceof Boolean || value instanceof Number) {
            writer.write(String.valueOf(value));
            return;
        }
        if (value instanceof Collection<?> list) {
            collectionCodec.write((Collection<Object>) list, context);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            mapCodec.write((Map) map, context);
            return;
        }
        throw new IllegalStateException("Unsupported type: " + value);
    }
}
