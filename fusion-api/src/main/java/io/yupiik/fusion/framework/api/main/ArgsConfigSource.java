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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Maps the dash-led launcher arguments to configuration entries, tolerating {@code --key value},
 * {@code -key value} and the equals forms ({@code --key=value}); bare positional tokens (command
 * path segments of a CLI application for example) are ignored. A value which looks like an option is
 * never consumed, so {@code --foo --bar} is rejected instead of pairing them.
 * <p>
 * Every dash-led option requires a value: a bare {@code --x} (last argument or directly followed by
 * another option, negative numbers like {@code -1} are values) throws an
 * {@link IllegalArgumentException} at construction time - except the exact {@code --help} token
 * which is skipped, and the {@code --no-} form which implicitly binds {@code false} (see below).
 * <p>
 * Keys are normalized once to their dash form in the constructor and indexed with both their dash
 * and dot aliases so {@link #get(String)} - called very often - only does map lookups. A bare key
 * starting with {@code no-} ({@code --no-foo} or {@code --no.foo}) keeps its value under
 * {@code no-foo}/{@code no.foo} and exposes it as the default of the positive key
 * ({@code get("foo")} returns {@code false}); a negation never consumes the following token
 * ({@code --no-x v} binds {@code x=false} and drops {@code v}). Use {@code --no-x=value} for an
 * explicit value, which then only binds {@code no-x} like any other key.
 */
public class ArgsConfigSource implements ConfigurationSource {
    private final Map<String, String> values;  // key (dash and dot aliases) -> joined value
    private final Map<String, String> negated; // positive alias of a no- key -> its value (false usually)

    public ArgsConfigSource(final List<String> args) {
        final var parsed = new HashMap<String, List<String>>();
        final var negations = new HashSet<String>();
        final var len = args.size();
        for (int i = 0; i < len; i++) {
            final var token = args.get(i);
            if (!isOptionLike(token)) { // positional token (command name, bare value...), not a config entry
                continue;
            }
            if ("--help".equals(token)) { // help is a CLI concern, not a configuration entry
                continue;
            }
            final var rawName = token.startsWith("--") ? token.substring(2) : token.substring(1);
            final int sep = rawName.indexOf('=');
            final var name = (sep >= 0 ? rawName.substring(0, sep) : rawName).replace('.', '-');
            if (name.isEmpty()) { // lone "-"/"--"
                continue;
            }
            if (sep >= 0) {
                handle(parsed, name, rawName.substring(sep + 1)); // value untouched (may hold dots/paths)
            } else if (name.startsWith("no-") && name.length() > "no-".length()) { // negation marker
                negations.add(name);
                handle(parsed, name, "false");
            } else if (i + 1 < len && !isOptionLike(args.get(i + 1))) {
                handle(parsed, name, args.get(++i));
            } else {
                throw new IllegalArgumentException("Option '--" + name + "' requires a value (use '--" + name + "=value')");
            }
        }

        this.values = new HashMap<>();
        this.negated = new HashMap<>();
        for (final var entry : parsed.entrySet()) {
            final var key = entry.getKey();
            final var effective = String.join(",", entry.getValue());
            final var alias = key.replace('-', '.');
            values.put(key, effective);
            if (!alias.equals(key)) {
                values.put(alias, effective);
            }
            if (negations.contains(key)) { // map the positive key to the negation value too
                final var remainder = key.substring("no-".length());
                negated.put(remainder, effective);
                if (remainder.indexOf('-') >= 0) {
                    negated.put(remainder.replace('-', '.'), effective);
                }
            }
        }
    }

    /**
     * @param token the candidate value.
     * @return {@code true} when the token looks like an option (starts with {@code -} but is not a
     * negative number like {@code -1}), {@code false} for bare values.
     */
    public static boolean isOptionLike(final String token) {
        return token != null && token.length() > 1 && token.charAt(0) == '-' && !Character.isDigit(token.charAt(1));
    }

    @Override
    public String get(final String key) {
        var value = values.get(key);
        if (value != null) {
            return value;
        }
        value = negated.get(key);
        if (value != null) {
            return value;
        }
        final var normalized = stripLeadingDashes(key); // "--foo-bar", "-test" or legacy "-.foo.bar" lookups
        if (!normalized.equals(key)) {
            value = values.get(normalized);
            if (value != null) {
                return value;
            }
            value = negated.get(normalized);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String stripLeadingDashes(final String key) {
        if (key.startsWith("--")) {
            return key.substring(2);
        }
        if (key.startsWith("-.")) {
            return key.substring(2);
        }
        if (key.startsWith("-")) {
            return key.substring(1);
        }
        return key;
    }

    private void handle(final Map<String, List<String>> target, final String key, final String value) {
        handle(target, key, value, new HashSet<>());
    }

    private void handle(final Map<String, List<String>> target, final String key, final String value,
                        final Set<String> visited) {
        final var normalized = key.replace('.', '-');
        if (!visited.add(normalized)) {
            return;
        }
        if (normalized.startsWith("fusion-properties")) {
            final var path = Path.of(value);
            final var props = new Properties();
            try (final var reader = Files.exists(path) ? Files.newBufferedReader(path) : new StringReader(value)) {
                props.load(reader);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            props.stringPropertyNames().forEach(k -> handle(target, k, props.getProperty(k), visited));
        } else {
            target.computeIfAbsent(normalized, in -> new ArrayList<>()).add(value);
        }
    }
}