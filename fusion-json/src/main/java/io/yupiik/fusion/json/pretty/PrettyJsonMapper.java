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
package io.yupiik.fusion.json.pretty;

import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.internal.formatter.SimplePrettyFormatter;
import io.yupiik.fusion.json.internal.io.FastStringWriter;
import io.yupiik.fusion.json.mapper.DelegatingMapper;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

public class PrettyJsonMapper extends DelegatingMapper {
    private final Charset charset;
    private final Function<String, String> prettifier;

    public PrettyJsonMapper(final JsonMapper mapper) {
        this(mapper, StandardCharsets.UTF_8);
    }

    public PrettyJsonMapper(final JsonMapper mapper, final Charset charset) {
        super(mapper);
        this.charset = charset;
        this.prettifier = new SimplePrettyFormatter(mapper);
    }

    private String prettify(final String json) {
        return prettifier.apply(json);
    }

    @Override
    public <A> byte[] toBytes(final A instance) {
        return prettify(new String(super.toBytes(instance), charset)).getBytes(charset);
    }

    @Override
    public <A> String toString(final A instance) {
        return prettify(super.toString(instance));
    }

    @Override
    public <A> void write(final A instance, final Writer out) {
        final var writer = new FastStringWriter(new StringBuilder());
        try (writer) {
            super.write(instance, writer);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        try {
            out.write(prettify(writer.toString()));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
