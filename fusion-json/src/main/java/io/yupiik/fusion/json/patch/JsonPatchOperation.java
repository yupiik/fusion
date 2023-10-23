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
package io.yupiik.fusion.json.patch;

import io.yupiik.fusion.framework.api.container.Generation;
import io.yupiik.fusion.json.internal.codec.BaseJsonCodec;
import io.yupiik.fusion.json.internal.codec.CollectionJsonCodec;
import io.yupiik.fusion.json.spi.Parser;

import java.io.IOException;
import java.util.ArrayList;

import static io.yupiik.fusion.json.spi.Parser.Event.START_OBJECT;

// @JsonModel
public record JsonPatchOperation(Operation op, String path, String from, Object value) {
    // @JsonModel
    public enum Operation {
        add, remove, replace, move, copy, test
    }

    @Generation(version = 1)
    public static class Codec extends BaseJsonCodec<JsonPatchOperation> {
        private static final char[] op__CHAR_ARRAY = "\"op\":".toCharArray();
        private static final char[] path__CHAR_ARRAY = "\"path\":".toCharArray();
        private static final char[] from__CHAR_ARRAY = "\"from\":".toCharArray();
        private static final char[] value__CHAR_ARRAY = "\"value\":".toCharArray();

        public Codec() {
            super(JsonPatchOperation.class);
        }

        @Override
        public JsonPatchOperation read(final DeserializationContext context) throws IOException {
            final var parser = context.parser();
            parser.enforceNext(START_OBJECT);

            JsonPatchOperation.Operation param__op = null;
            String path = null;
            String from = null;
            Object value = null;

            String key = null;
            Parser.Event event;
            while (parser.hasNext()) {
                event = parser.next();
                switch (event) {
                    case KEY_NAME:
                        key = parser.getString();
                        break;
                    case VALUE_STRING:
                        switch (key) {
                            case "op":
                                parser.rewind(event);
                                param__op = context.codec(JsonPatchOperation.Operation.class).read(context);
                                break;
                            case "path":
                                path = parser.getString();
                                break;
                            case "from":
                                from = parser.getString();
                                break;
                            case "value":
                                value = parser.getString();
                                break;
                            default: // ignore
                        }
                        key = null;
                        break;
                    case START_OBJECT:
                        switch (key) {
                            case "value":
                                parser.rewind(event);
                                value = context.codec(java.lang.Object.class).read(context);
                                break;
                            default:
                                parser.skipObject();
                                break;
                        }
                        key = null;
                        break;
                    case END_OBJECT:
                        return new JsonPatchOperation(param__op, path, from, value);
                    case VALUE_NUMBER:
                        switch (key) {
                            case "value":
                                value = parser.getBigDecimal();
                                break;
                            default:
                        }
                        key = null;
                        break;
                    case VALUE_TRUE:
                        switch (key) {
                            case "value":
                                value = true;
                                break;
                            default:
                        }
                        key = null;
                        break;
                    case VALUE_FALSE:
                        switch (key) {
                            case "value":
                                value = false;
                                break;
                            default:
                        }
                        key = null;
                        break;
                    case VALUE_NULL:
                        key = null;
                        break;
                    case START_ARRAY:
                        switch (key) {
                            case "value":
                                context.parser().rewind(event);
                                value = new CollectionJsonCodec<>(context.codec(Object.class), Object.class, ArrayList::new).read(context);
                                break;
                            default:
                                parser.skipArray();
                                key = null;
                        }
                        break;
                    // case END_ARRAY: fallthrough
                    default:
                        throw new IllegalArgumentException("Unsupported event: " + event);
                }
            }
            throw new IllegalArgumentException("Object didn't end.");
        }

        @Override
        public void write(final JsonPatchOperation instance, final SerializationContext context) throws IOException {
            final var writer = context.writer();
            boolean firstAttribute = true;
            writer.write('{');
            if (instance.from() != null) {
                firstAttribute = false;
                writer.write(from__CHAR_ARRAY);
                writer.write(io.yupiik.fusion.json.internal.JsonStrings.escapeChars(instance.from()));
            }
            if (instance.op() != null) {
                if (firstAttribute) {
                    firstAttribute = false;
                } else {
                    writer.write(',');
                }
                writer.write(op__CHAR_ARRAY);
                context.codec(JsonPatchOperation.Operation.class).write(instance.op(), context);
            }
            if (instance.path() != null) {
                if (firstAttribute) {
                    firstAttribute = false;
                } else {
                    writer.write(',');
                }
                writer.write(path__CHAR_ARRAY);
                writer.write(io.yupiik.fusion.json.internal.JsonStrings.escapeChars(instance.path()));
            }
            if (instance.value() != null) {
                if (!firstAttribute) {
                    writer.write(',');
                }
                final var codec = context.codec(Object.class);
                writer.write(value__CHAR_ARRAY);
                codec.write(instance.value(), context);
            }
            writer.write('}');
        }
    }


}
