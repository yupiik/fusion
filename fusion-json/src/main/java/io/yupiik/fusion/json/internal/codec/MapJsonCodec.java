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

import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.json.internal.JsonStrings;
import io.yupiik.fusion.json.internal.parser.JsonParser;
import io.yupiik.fusion.json.serialization.JsonCodec;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.yupiik.fusion.json.spi.Parser.Event.END_OBJECT;
import static io.yupiik.fusion.json.spi.Parser.Event.KEY_NAME;
import static io.yupiik.fusion.json.spi.Parser.Event.START_OBJECT;

public class MapJsonCodec<A> implements JsonCodec<Map<String, A>> {
    private final JsonCodec<A> delegate;
    private final Type type;

    public MapJsonCodec(final JsonCodec<A> delegate) {
        this.delegate = delegate;
        this.type = new Types.ParameterizedTypeImpl(Map.class, String.class, delegate.type());
    }

    @Override
    public Type type() {
        return type;
    }

    @Override
    public Map<String, A> read(final DeserializationContext context) throws IOException {
        final var reader = context.parser();
        reader.enforceNext(START_OBJECT);

        final var instance = new LinkedHashMap<String, A>();
        JsonParser.Event event;
        while (reader.hasNext() && (event = reader.next()) != END_OBJECT) {
            reader.rewind(event);

            final var keyEvent = reader.next();
            if (keyEvent != KEY_NAME) {
                throw new IllegalStateException("Expected=KEY_NAME, but got " + keyEvent);
            }
            instance.put(reader.getString(), delegate.read(context));
        }
        return instance;
    }

    @Override
    public void write(final Map<String, A> value, final SerializationContext context) throws IOException {
        final var writer = context.writer();
        final var it = value.entrySet().iterator();
        writer.write('{');
        while (it.hasNext()) {
            final var entry = it.next();
            if (entry == null) {
                continue;
            }

            writer.write(JsonStrings.escapeChars(entry.getKey()));
            writer.write(":");
            if (entry.getValue() == null) {
                writer.write("null");
            } else {
                delegate.write(entry.getValue(), context);
            }
            if (it.hasNext()) {
                writer.write(',');
            }
        }
        writer.write('}');
    }
}
