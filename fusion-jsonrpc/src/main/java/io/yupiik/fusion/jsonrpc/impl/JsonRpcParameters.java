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
package io.yupiik.fusion.jsonrpc.impl;

import io.yupiik.fusion.jsonrpc.JsonRpcException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Parameter lookup and validation shared by generated direct JSON-RPC callbacks. */
public final class JsonRpcParameters {
    private JsonRpcParameters() {
    }

    /**
     * Finds a raw value before the generated typed helper performs conversions.
     * Position is the JSON parameter position, excluding injected Request arguments.
     * A missing optional value is returned as null; a missing required value keeps the legacy error code.
     */
    public static Object findParameter(final JsonRpcMethod.Context context, final String name,
                                       final int position, final boolean required) {
        final var params = context.params();
        final Object value;
        if (params == null) {
            value = null;
        } else if (params instanceof List<?> list) {
            value = list.size() > position ? list.get(position) : null;
        } else if (params instanceof Map<?, ?> map) {
            value = map.get(name);
        } else {
            throw new JsonRpcException(-32700,
                    "Invalid parameter instance, expected an array or an object but got '" + params.getClass().getSimpleName() + "'");
        }
        if (value == null && required) {
            throw new JsonRpcException(-32601, "Missing '" + name + "' parameter.");
        }
        return value;
    }

    /** Validates a value using the supplied predicate without converting it. */
    public static Object failIfNot(final String name, final Object value, final Predicate<Object> validator) {
        if (!validator.test(value)) {
            throw invalidParameter(name, value);
        }
        return value;
    }

    /** Validates a non-null collection value before generated element conversions. */
    public static Collection<?> failIfNotCollection(final String name, final Object value) {
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        throw invalidParameter(name, value);
    }

    /** Validates a non-null object value before generated value conversions. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> failIfNotMap(final String name, final Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw invalidParameter(name, value);
    }

    private static JsonRpcException invalidParameter(final String name, final Object value) {
        return new JsonRpcException(-32700,
                "Invalid parameter '" + name + "', got a " + value.getClass().getSimpleName());
    }

}
