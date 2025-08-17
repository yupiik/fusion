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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

public class DefaultJsonRpcMethod implements JsonRpcMethod {
    private final int priority;
    private final String jsonRpcMethod;
    private final Function<Context, CompletionStage<?>> invoker;
    private final Map<String, String> metadata;
    private final boolean isNotification;

    public DefaultJsonRpcMethod(final int priority, final String jsonRpcMethod, final Function<Context, CompletionStage<?>> invoker) {
        this(priority, jsonRpcMethod, invoker, false, Map.of());
    }

    public DefaultJsonRpcMethod(final int priority, final String jsonRpcMethod, final Function<Context, CompletionStage<?>> invoker, final boolean isNotification, final Map<String, String> metadata) {
        this.priority = priority;
        this.jsonRpcMethod = jsonRpcMethod;
        this.invoker = invoker;
        this.isNotification = isNotification;
        this.metadata = metadata;
    }

    @Override
    public boolean isNotification() {
        return isNotification;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public String name() {
        return jsonRpcMethod;
    }

    @Override
    public CompletionStage<?> invoke(final Context context) {
        return invoker.apply(context);
    }

    @Override
    public Map<String, String> metadata() {
        return metadata;
    }

    // todo: we can handle it as a bulk lookup which can be a bit faster (not critical at all, very very minor)
    // DO NOT DELETE, USED BY SUBCLASSES/GENERATION
    protected static <T> T findParameter(final Context context,
                                         final String name, final int position,
                                         final boolean required,
                                         final Function<Object, T> mapper) {
        final var params = context.params();
        final var value = params == null ? null : doFindParameter(params, name, position);
        if (value == null && required) {
            throw new JsonRpcException(-32601, "Missing '" + name + "' parameter.");
        }
        return mapper.apply(value);
    }

    private static Object doFindParameter(final Object params, final String name, final int position) {
        if (params instanceof List<?> list) {
            return list.size() > position ? list.get(position) : null;
        }
        if (params instanceof Map<?, ?> map) {
            return map.get(name);
        }
        throw new JsonRpcException(
                -32700,
                "Invalid parameter instance, expected an array or an object but got '" + (params == null ? "null" : params.getClass().getSimpleName()) + "'");
    }

    protected static Object failIfNot(final String name, final Object value, final Predicate<Object> validator) {
        if (!validator.test(value)) {
            throw new JsonRpcException(
                    -32700,
                    "Invalid parameter '" + name + "', got a " + value.getClass().getSimpleName());
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> failIfNotMap(final String name, final Object value) {
        return (Map<String, Object>) failIfNot(name, value, it -> it instanceof Map<?, ?>);
    }
}
