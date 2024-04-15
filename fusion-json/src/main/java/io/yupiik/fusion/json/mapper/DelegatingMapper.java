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
package io.yupiik.fusion.json.mapper;

import io.yupiik.fusion.json.JsonMapper;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.Optional;

public class DelegatingMapper implements JsonMapper {
    private final JsonMapper mapper;

    public DelegatingMapper(final JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public <T> Optional<T> as(final Class<T> type) {
        return mapper.as(type);
    }

    @Override
    public <A> byte[] toBytes(final A instance) {
        return mapper.toBytes(instance);
    }

    @Override
    public <A> A fromBytes(final Class<A> type, final byte[] bytes) {
        return mapper.fromBytes(type, bytes);
    }

    @Override
    public <A> A fromBytes(final Type type, final byte[] bytes) {
        return mapper.fromBytes(type, bytes);
    }

    @Override
    public <A> A fromString(final Class<A> type, final String string) {
        return mapper.fromString(type, string);
    }

    @Override
    public <A> A fromString(final Type type, final String string) {
        return mapper.fromString(type, string);
    }

    @Override
    public <A> String toString(final A instance) {
        return mapper.toString(instance);
    }

    @Override
    public <A> void write(final A instance, final Writer writer) {
        mapper.write(instance, writer);
    }

    @Override
    public <A> A read(final Type type, final Reader rawReader) {
        return mapper.read(type, rawReader);
    }

    @Override
    public <A> A read(final Class<A> type, final Reader reader) {
        return mapper.read(type, reader);
    }

    @Override
    public void close() {
        mapper.close();
    }
}
