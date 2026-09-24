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
package io.yupiik.fusion.framework.handlebars.compiler.part;

import io.yupiik.fusion.framework.handlebars.helper.HelperContext;
import io.yupiik.fusion.framework.handlebars.spi.Accessor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Compile-time argument parser: splits a raw argument string into positionals and {@code key=value} hash args,
 * resolving sub-expressions like {@code (eq a b)} to their helper at compile time.
 * Nothing is parsed at render time.
 */
public final class Helpers {
    private final Map<String, Function<HelperContext, Object>> helpers;

    public Helpers(final Map<String, Function<HelperContext, Object>> helpers) {
        this.helpers = helpers;
    }

    public boolean isHelper(final String name) {
        return helpers.containsKey(name);
    }

    public Function<HelperContext, Object> helper(final String name) {
        return helpers.get(name);
    }

    public ParsedArgs parseArgs(final String raw) {
        final var args = new ArrayList<ArgEvaluator>();
        final var hash = new HashMap<String, ArgEvaluator>();
        for (final var token : tokenize(raw.strip())) {
            if (token instanceof RawArg ra) {
                args.add(toEvaluator(ra.value(), ra.quoted()));
            } else if (token instanceof RawHash rh) {
                hash.put(rh.key(), toEvaluator(rh.value(), rh.quoted()));
            } else {
                throw new IllegalArgumentException("Unexpected token: " + token);
            }
        }
        return new ParsedArgs(List.copyOf(args), Map.copyOf(hash));
    }

    public Map<String, ArgEvaluator> parseHashArgs(final String raw) {
        final var hash = new HashMap<String, ArgEvaluator>();
        for (final var token : tokenize(raw.strip())) {
            if (token instanceof RawHash rh) {
                hash.put(rh.key(), toEvaluator(rh.value(), rh.quoted()));
            } else {
                throw new IllegalArgumentException("Parameters must be key=value pairs: '" + raw + "'");
            }
        }
        return Map.copyOf(hash);
    }

    /**
     * Pure helper shared by the parts to evaluate positional arguments; not tied to a parsed template
     * so it can stay a static utility.
     */
    public static List<Object> evalArgs(final List<ArgEvaluator> args, final Accessor accessor, final Object current) {
        return evalArgs(args, accessor, current, null);
    }

    public static List<Object> evalArgs(final List<ArgEvaluator> args, final Accessor accessor, final Object current,
                                        final Part.RenderContext context) {
        return args.stream().map(it -> it.eval(accessor, current, context)).toList();
    }

    public static Map<String, Object> evalHash(final Map<String, ArgEvaluator> hash, final Accessor accessor, final Object current) {
        return evalHash(hash, accessor, current, null);
    }

    public static Map<String, Object> evalHash(final Map<String, ArgEvaluator> hash, final Accessor accessor, final Object current,
                                               final Part.RenderContext context) {
        if (hash.isEmpty()) {
            return Map.of();
        }
        final var out = new HashMap<String, Object>(hash.size());
        for (final var entry : hash.entrySet()) {
            out.put(entry.getKey(), entry.getValue().eval(accessor, current, context));
        }
        return out;
    }

    public record ParsedArgs(List<ArgEvaluator> args, Map<String, ArgEvaluator> hash) {
    }

    private ArgEvaluator toEvaluator(final String value, final boolean quoted) {
        if (quoted) {
            return (accessor, current, context) -> value;
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Empty argument");
        }
        if ("true".equals(value)) {
            return (accessor, current, context) -> Boolean.TRUE;
        }
        if ("false".equals(value)) {
            return (accessor, current, context) -> Boolean.FALSE;
        }
        if (isInteger(value)) {
            final var constant = parseNumber(value);
            return (accessor, current, context) -> constant;
        }
        if (value.endsWith("L")) { // long literal: 1L
            final var number = value.substring(0, value.length() - 1);
            if (isInteger(number)) {
                final var constant = Long.parseLong(number);
                return (accessor, current, context) -> constant;
            }
        }
        if (isDecimal(value)) {
            final var constant = new BigDecimal(value);
            return (accessor, current, context) -> constant;
        }
        if (value.charAt(0) == '(') {
            return toSubExpression(value);
        }
        return new DynamicArgEvaluator(value);
    }

    private ArgEvaluator toSubExpression(final String value) {
        if (!value.endsWith(")")) {
            throw new IllegalArgumentException("Unbalanced parenthesis in '" + value + "'");
        }
        final var inner = value.substring(1, value.length() - 1).strip();
        final int space = inner.indexOf(' ');
        final String name;
        final String args;
        if (space < 0) {
            name = inner;
            args = "";
        } else {
            name = inner.substring(0, space).strip();
            args = inner.substring(space).strip();
        }
        final var helper = helpers.get(name);
        if (helper == null) {
            throw new IllegalArgumentException("No helper '" + name + "'");
        }
        final var parsed = parseArgs(args);
        return new SubExpressionArgEvaluator(helper, parsed.args(), parsed.hash());
    }

    private Object parseNumber(final String value) {
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException nfe) {
            try {
                return Long.parseLong(value);
            } catch (final NumberFormatException nfe2) {
                return new BigDecimal(value);
            }
        }
    }

    private boolean isInteger(final String value) {
        if (value.isEmpty() || "-".equals(value)) {
            return false;
        }
        for (var i = value.charAt(0) == '-' ? 1 : 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isDecimal(final String value) {
        if (value.isEmpty() || "-".equals(value)) {
            return false;
        }
        var digits = 0;
        for (var i = value.charAt(0) == '-' ? 1 : 0; i < value.length(); i++) {
            final var c = value.charAt(i);
            if (!Character.isDigit(c) && c != '.') {
                return false;
            }
            if (Character.isDigit(c)) {
                digits++;
            }
        }
        return digits > 0 && value.indexOf('.') > 0;
    }

    private List<Object> tokenize(final String raw) {
        final var out = new ArrayList<Object>();
        final var value = new StringBuilder();
        var hashKey = (String) null;
        var argQuoted = false;
        var hashQuoted = false;
        var quoted = false;
        var quoteChar = (char) 0;
        var escaped = false;
        var depth = 0;
        for (var i = 0; i < raw.length(); i++) {
            final var c = raw.charAt(i);
            if (escaped) {
                value.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (quoted) {
                if (c == quoteChar) {
                    quoted = false;
                } else {
                    value.append(c);
                }
            } else if (c == '(') {
                depth++;
                value.append(c);
            } else if (c == ')') {
                if (depth == 0) {
                    throw new IllegalArgumentException("Unbalanced parenthesis in '" + raw + "'");
                }
                depth--;
                value.append(c);
            } else if (c == ' ' && depth == 0) {
                flush(out, value, hashKey, argQuoted, hashQuoted);
                hashKey = null;
                argQuoted = false;
                hashQuoted = false;
            } else if (c == '=' && depth == 0 && hashKey == null) {
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("Invalid hash argument in '" + raw + "'");
                }
                hashKey = value.toString();
                value.setLength(0);
            } else if (c == '"' || c == '\'') {
                if (depth > 0) { // inside a sub-expression: keep the quotes for the recursive parse
                    value.append(c);
                } else {
                    if (!value.isEmpty()) {
                        throw new IllegalArgumentException("Quote in the middle of an argument in '" + raw + "'");
                    }
                    quoted = true;
                    quoteChar = c;
                    if (hashKey == null) {
                        argQuoted = true;
                    } else {
                        hashQuoted = true;
                    }
                }
            } else {
                value.append(c);
            }
        }
        if (escaped || quoted || depth != 0) {
            throw new IllegalArgumentException("Malformed arguments '" + raw + "'");
        }
        flush(out, value, hashKey, argQuoted, hashQuoted);
        return out;
    }

    private void flush(final List<Object> out, final StringBuilder value, final String hashKey,
                       final boolean argQuoted, final boolean hashQuoted) {
        if (hashKey == null) {
            if (!value.isEmpty() || argQuoted) {
                out.add(new RawArg(value.toString(), argQuoted));
            }
        } else {
            out.add(new RawHash(hashKey, value.toString(), hashQuoted));
        }
        value.setLength(0);
    }

    private record RawArg(String value, boolean quoted) {
    }

    private record RawHash(String key, String value, boolean quoted) {
    }
}