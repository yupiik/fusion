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
package io.yupiik.fusion.framework.handlebars.helper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Built-in helpers merged with the user ones (user helpers override the built-ins by name).
 * All of them are usable inline, as block helpers (then the block - or its `{{else}}` branch for
 * conditional helpers - is rendered) and inside sub-expressions like `{{#if (eq a b)}}`.
 */
public final class DefaultHelpers {
    private static final String DEFAULT_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final long EXACT_LONG_AS_DOUBLE = 1L << 53; // 2^53: longs with |value| <= 2^53 are exactly representable as double

    private final Truthiness truthiness = Truthiness.INSTANCE;
    private final Map<String, Function<HelperContext, Object>> helpers;

    public DefaultHelpers() {
        final Map<String, Function<HelperContext, Object>> tmp = new HashMap<>();
        tmp.put("eq", this::eq);
        tmp.put("ne", this::ne);
        tmp.put("gt", this::gt);
        tmp.put("gte", this::gte);
        tmp.put("lt", this::lt);
        tmp.put("lte", this::lte);
        tmp.put("and", this::and);
        tmp.put("or", this::or);
        tmp.put("not", this::not);
        tmp.put("default", this::def); // 'default' is a Java keyword, the method is named def
        tmp.put("lookup", this::lookup);
        tmp.put("time", this::time);
        this.helpers = Map.copyOf(tmp);
    }

    public Map<String, Function<HelperContext, Object>> helpers() {
        return helpers;
    }

    public Object eq(final HelperContext ctx) {
        requireArity(ctx, 2, "eq");
        return booleanResult(ctx, equal(ctx.args().get(0), ctx.args().get(1)));
    }

    public Object ne(final HelperContext ctx) {
        requireArity(ctx, 2, "ne");
        return booleanResult(ctx, !equal(ctx.args().get(0), ctx.args().get(1)));
    }

    public Object gt(final HelperContext ctx) {
        requireArity(ctx, 2, "gt");
        return booleanResult(ctx, compare(ctx.args().get(0), ctx.args().get(1), "gt") > 0);
    }

    public Object gte(final HelperContext ctx) {
        requireArity(ctx, 2, "gte");
        return booleanResult(ctx, compare(ctx.args().get(0), ctx.args().get(1), "gte") >= 0);
    }

    public Object lt(final HelperContext ctx) {
        requireArity(ctx, 2, "lt");
        return booleanResult(ctx, compare(ctx.args().get(0), ctx.args().get(1), "lt") < 0);
    }

    public Object lte(final HelperContext ctx) {
        requireArity(ctx, 2, "lte");
        return booleanResult(ctx, compare(ctx.args().get(0), ctx.args().get(1), "lte") <= 0);
    }

    public Object and(final HelperContext ctx) {
        var result = true;
        for (final var arg : ctx.args()) {
            if (truthiness.isFalsy(arg)) {
                result = false;
                break;
            }
        }
        return booleanResult(ctx, result);
    }

    public Object or(final HelperContext ctx) {
        var result = false;
        for (final var arg : ctx.args()) {
            if (!truthiness.isFalsy(arg)) {
                result = true;
                break;
            }
        }
        return booleanResult(ctx, result);
    }

    public Object not(final HelperContext ctx) {
        requireArity(ctx, 1, "not");
        return booleanResult(ctx, truthiness.isFalsy(ctx.args().get(0)));
    }

    public Object def(final HelperContext ctx) {
        requireArity(ctx, 2, "default");
        final var value = ctx.args().get(0);
        return valueOrBlock(ctx, truthiness.isFalsy(value) ? ctx.args().get(1) : value);
    }

    public Object lookup(final HelperContext ctx) {
        requireArity(ctx, 2, "lookup");
        final var object = ctx.args().get(0);
        final var key = ctx.args().get(1);
        final Object value;
        if (object instanceof Map<?, ?> map) {
            value = map.get(key);
        } else if (object instanceof List<?> list && key instanceof Number number) {
            final var index = number.intValue();
            value = index >= 0 && index < list.size() ? list.get(index) : null;
        } else {
            value = null;
        }
        return valueOrBlock(ctx, value);
    }

    public Object time(final HelperContext ctx) {
        if (ctx.args().size() > 1) {
            throw new IllegalArgumentException("time expects 0 or 1 argument, got " + ctx.args().size());
        }
        final var pattern = ctx.hash().containsKey("pattern") ? asString(ctx.hash().get("pattern"), "pattern") : DEFAULT_TIME_PATTERN;
        final var value = ctx.args().isEmpty() ? Instant.now() : ctx.args().get(0);
        return valueOrBlock(ctx, format(value, pattern));
    }

    private String format(final Object value, final String pattern) {
        final var formatter = DateTimeFormatter.ofPattern(pattern);
        if (value instanceof Instant instant) {
            return formatter.withZone(ZoneId.systemDefault()).format(instant);
        }
        if (value instanceof Date date) {
            return formatter.withZone(ZoneId.systemDefault()).format(date.toInstant());
        }
        if (value instanceof Number number) {
            return formatter.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(number.longValue()));
        }
        if (value instanceof LocalDate localDate) {
            return formatter.format(localDate);
        }
        if (value instanceof LocalTime localTime) {
            return formatter.format(localTime);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return formatter.format(localDateTime);
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return formatter.format(offsetDateTime);
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return formatter.format(zonedDateTime);
        }
        throw new IllegalArgumentException("Unsupported time value: " + value + " (" + (value == null ? "null" : value.getClass().getName()) + ")");
    }

    private String asString(final Object value, final String name) {
        if (value instanceof String s) {
            return s;
        }
        throw new IllegalArgumentException(name + " must be a string, got " + value);
    }

    private Object valueOrBlock(final HelperContext ctx, final Object value) {
        if (ctx.blockRenderer() == null) {
            return value;
        }
        return ctx.blockRenderer().apply(value);
    }

    private Object booleanResult(final HelperContext ctx, final boolean result) {
        if (ctx.blockRenderer() == null) {
            return result;
        }
        if (result) {
            return ctx.blockRenderer().apply(null);
        }
        return ctx.inverseRenderer() == null ? "" : ctx.inverseRenderer().apply(null);
    }

    private void requireArity(final HelperContext ctx, final int expected, final String name) {
        if (ctx.args().size() != expected) {
            throw new IllegalArgumentException(name + " expects " + expected + " argument(s), got " + ctx.args().size());
        }
    }

    private boolean equal(final Object left, final Object right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left instanceof Number l && right instanceof Number r) {
            return compareTo(l, r) == 0;
        }
        return left.equals(right);
    }

    private int compare(final Object left, final Object right, final String operator) {
        if (left instanceof Number l && right instanceof Number r) {
            return compareTo(l, r);
        }
        if (left instanceof Number l && right instanceof String rs) {
            return toDecimal(l).compareTo(toDecimal(rs, operator));
        }
        if (left instanceof String ls && right instanceof Number r) {
            return toDecimal(ls, operator).compareTo(toDecimal(r));
        }
        throw new IllegalArgumentException(operator + " requires numbers, got " + left + " and " + right);
    }

    private int compareTo(final Number left, final Number right) {
        if (isIntegral(left) && isIntegral(right)) {
            return Long.compare(left.longValue(), right.longValue());
        }
        if (isFloating(left) && isFloating(right)) {
            final var l = left.doubleValue();
            final var r = right.doubleValue();
            if (Double.isFinite(l) && Double.isFinite(r)) {
                return Double.compare(l, r);
            }
        }
        if (isIntegral(left) && isFloating(right)) {
            return compareIntegralFloating(left.longValue(), right.doubleValue());
        }
        if (isFloating(left) && isIntegral(right)) {
            return -compareIntegralFloating(right.longValue(), left.doubleValue());
        }
        return toDecimal(left).compareTo(toDecimal(right));
    }

    private int compareIntegralFloating(final long left, final double right) {
        if (Double.isFinite(right) && left >= -EXACT_LONG_AS_DOUBLE && left <= EXACT_LONG_AS_DOUBLE) {
            return Double.compare(left, right);
        }
        return toDecimal(left).compareTo(toDecimal(right));
    }

    private boolean isIntegral(final Number value) {
        return value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
    }

    private boolean isFloating(final Number value) {
        return value instanceof Float || value instanceof Double;
    }

    private BigDecimal toDecimal(final Number value) {
        try {
            return new BigDecimal(value.toString());
        } catch (final NumberFormatException nfe) {
            throw new IllegalArgumentException("Comparison requires numbers, got " + value, nfe);
        }
    }

    private BigDecimal toDecimal(final Object value, final String operator) {
        try {
            return new BigDecimal(value.toString().trim());
        } catch (final NumberFormatException nfe) {
            throw new IllegalArgumentException(operator + " requires numbers, got '" + value + "'", nfe);
        }
    }
}