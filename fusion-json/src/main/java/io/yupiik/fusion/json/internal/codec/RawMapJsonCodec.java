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
package io.yupiik.fusion.json.internal.codec;

import io.yupiik.fusion.json.JsonException;
import io.yupiik.fusion.json.serialization.JsonCodec;
import io.yupiik.fusion.json.spi.Parser;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

/** Retains the configured Object codec while enforcing raw Map's outer shape. */
public class RawMapJsonCodec implements JsonCodec<Object> {
    private final JsonCodec<?> delegate;

    public RawMapJsonCodec(final JsonCodec<?> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Type type() {
        return Map.class;
    }

    @Override
    public Object read(final DeserializationContext context) throws IOException {
        if (context.rejectIncompatibleValues()) {
            final var parser = context.parser();
            if (!parser.hasNext()) {
                throw new JsonException("Expected a JSON object or null");
            }
            final var event = parser.next();
            if (event != Parser.Event.START_OBJECT && event != Parser.Event.VALUE_NULL) {
                throw new JsonException("Expected a JSON object or null but got " + event);
            }
            parser.rewind(event);
        }
        return delegate.read(context);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void write(final Object value, final SerializationContext context) throws IOException {
        ((JsonCodec<Object>) delegate).write(value, context);
    }
}
