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

import io.yupiik.fusion.json.internal.parser.JsonParser;
import io.yupiik.fusion.json.serialization.JsonCodec;
import io.yupiik.fusion.json.spi.Parser;

import java.io.IOException;
import java.lang.reflect.Type;

import static io.yupiik.fusion.json.spi.Parser.Event.VALUE_NUMBER;

public abstract class NumberJsonCodec<A> implements JsonCodec<A> {
    private final Class<A> type;

    public NumberJsonCodec(final Class<A> type) {
        this.type = type;
    }

    protected abstract A read(final Parser parser);

    @Override
    public Type type() {
        return type;
    }

    @Override
    public A read(final DeserializationContext context) {
        final var parser = context.parser();
        if (!parser.hasNext()) {
            throw new IllegalStateException("No more token.");
        }
        final JsonParser.Event event = parser.next();
        if (event != VALUE_NUMBER) {
            throw new IllegalStateException("Expected VALUE_NUMBER but got: " + event);
        }
        return read(parser);
    }

    @Override
    public void write(final A value, final SerializationContext context) throws IOException {
        context.writer().write(String.valueOf(value));
    }
}
