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
package io.yupiik.fusion.documentation;

import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.pretty.PrettyJsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;

public abstract class BaseOpenRPCConverter implements Runnable {
    protected final Map<String, String> configuration;

    public BaseOpenRPCConverter(final Map<String, String> configuration) {
        this.configuration = configuration;
    }

    protected abstract String convert(Map<String, Object> openrpc, JsonMapper mapper);

    @Override
    public void run() {
        final var input = Path.of(requireNonNull(configuration.get("input"), "No 'input'"));
        if (Files.notExists(input)) {
            throw new IllegalArgumentException("Input does not exist '" + input + "'");
        }
        final var output = Path.of(requireNonNull(configuration.get("output"), "No 'output'"));
        try (final var mapper = new PrettyJsonMapper(new JsonMapperImpl(List.of(), c -> empty()))) {
            final var openrpc = asObject(mapper.fromString(Object.class, preProcessInput(Files.readString(input))));
            final var adoc = convert(openrpc, mapper);
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            Files.writeString(output, adoc);
        } catch (final IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }

    protected String preProcessInput(final String input) {
        return input;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> asObject(final Object o) {
        return (Map<String, Object>) o;
    }

    protected Map<String, Object> sortedMap(final Map<String, Object> data) {
        final var out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        out.putAll(data);
        return out;
    }
}
