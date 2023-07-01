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
package io.yupiik.fusion.jwt.internal;

import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.jwt.Jwt;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;

import static java.util.Optional.ofNullable;

public class JwtImpl implements Jwt {
    private final String header;
    private final String payload;
    private final JsonMapper mapper;
    private final Map<String, Object> headerData;
    private final Map<String, Object> payloadData;

    public JwtImpl(final JsonMapper mapper, final String header, final String payload,
                   final Map<String, Object> headerData, final Map<String, Object> payloadData) {
        this.header = header;
        this.payload = payload;
        this.headerData = headerData;
        this.payloadData = payloadData;
        this.mapper = mapper;
    }

    @Override
    public String header() {
        return header;
    }

    @Override
    public String payload() {
        return payload;
    }

    @Override
    public Map<String, Object> headerData() {
        return headerData;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> claim(final String name, final Type type) {
        return ofNullable(payloadData.get(name))
                .map(value -> {
                    if (type instanceof Class<?> c) {
                        if (c.isInstance(value)) {
                            return (T) value;
                        }
                        throw new IllegalArgumentException(value.getClass() + " does not match " + type);
                    }
                    try { // mainly lists, avoids a costly type check
                        return (T) value;
                    } catch (final ClassCastException ce) {
                        throw new IllegalArgumentException(value.getClass() + " does not match " + type);
                    }
                });
    }
}
