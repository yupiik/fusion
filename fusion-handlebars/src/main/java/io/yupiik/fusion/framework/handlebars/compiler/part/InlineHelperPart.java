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

import io.yupiik.fusion.framework.handlebars.spi.Accessor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public record InlineHelperPart(Function<Object, String> helper, List<ArgEvaluator> args, Accessor accessor) implements Part {
    @Deprecated // for backward compatibility only
    public InlineHelperPart(final Function<Object, String> helper, final String name, final Accessor accessor) {
        this(helper, List.of(new DynamicArgEvaluator(name)), accessor);
    }

    @Override
    public String apply(final RenderContext context, final Object currentData) {
        if (args.size() == 1) {
            final var value = args.get(0).eval(accessor, currentData);
            if (value == null) {
                return "";
            }
            return helper.apply(value);
        }
        return helper.apply(List.of(args.stream().map(it -> it.eval(accessor, currentData)).toList()));
    }

    public static List<InlineHelperPart.ArgEvaluator> parseArgs(final String raw) {
        final var result = new ArrayList<ArgEvaluator>();

        Character end = null;
        boolean escaped = false;
        final var current = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            final char c = raw.charAt(i);
            if (escaped) {
                escaped = false;
                current.append(c);
            } else if ((end != null && end == c) || (c == ' ' && end == null)) {
                if (!current.isEmpty()) {
                    result.add(toEvaluator(current.toString(), end != null));
                    current.setLength(0);
                }
                end = null;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                end = c;
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            result.add(toEvaluator(current.toString(), false));
        }

        return result;
    }

    private static ArgEvaluator toEvaluator(final String value, final boolean quoted) {
        if (quoted) {
            return (accessor, current) -> value;
        }

        for (var i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return new DynamicArgEvaluator(value);
            }
        }

        final var v = Integer.parseInt(value);
        return (accessor, current) -> v;
    }

    public record DynamicArgEvaluator(String name) implements ArgEvaluator {
        @Override
        public Object eval(final Accessor accessor, final Object current) {
            final var value = ".".equals(name) || "this".equals(name) ? current : accessor.find(current, name);
            if (value == null) {
                return "";
            }
            return value;
        }
    }

    @FunctionalInterface
    public interface ArgEvaluator {
        Object eval(Accessor accessor, Object current);
    }
}
