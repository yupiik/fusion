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
package io.yupiik.fusion.framework.api.main;

import io.yupiik.fusion.framework.api.configuration.ConfigurationSource;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

// tolerate `--key value`, `key value`, `-key value` but also the same with equals (single arg) `--key=value`.
public class ArgsConfigSource implements ConfigurationSource {
    private final Map<String, List<String>> args;

    public ArgsConfigSource(final List<String> args) {
        this.args = new HashMap<>();
        final var len = args.size();
        for (int i = 0; i < len; i++) {
            var key = args.get(i);
            final int sep = key.indexOf('=');
            if (sep > 0) {
                handle(dropLeadingIphens(key.substring(0, sep)), key.substring(sep + 1));
            } else if (i + 1 < len) {
                i++;
                handle(dropLeadingIphens(key), args.get(i));
            }
        }
    }

    @Override
    public String get(final String key) {
        final var strings = args.get(key);
        return strings == null || strings.isEmpty() ? null : String.join(",", strings);
    }

    private void handle(final String sanitizedKey, final String value) {
        if (sanitizedKey.startsWith("fusion-properties")) {
            final var path = Path.of(value);
            final var props = new Properties();
            try (final var reader = Files.exists(path) ? Files.newBufferedReader(path) : new StringReader(value)) {
                props.load(reader);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            props.stringPropertyNames().forEach(k -> handle(k, props.getProperty(k)));
        } else {
            this.args.computeIfAbsent(sanitizedKey, in -> new ArrayList<>()).add(value);
        }
    }

    private String dropLeadingIphens(String key) {
        if (key.startsWith("--")) {
            return key.substring(2);
        }
        if (key.startsWith("-")) {
            return key.substring(1);
        }
        return key;
    }
}
