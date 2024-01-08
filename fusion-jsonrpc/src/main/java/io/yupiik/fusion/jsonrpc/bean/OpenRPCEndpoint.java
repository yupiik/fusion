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
package io.yupiik.fusion.jsonrpc.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.jsonrpc.impl.JsonRpcMethod;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Map.entry;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toMap;

/**
 * Enables to register OpenRPC spec as a JSON-RPC method.
 */
public class OpenRPCEndpoint extends BaseBean<OpenRPCEndpoint.Impl> implements FusionBean<OpenRPCEndpoint.Impl> {
    private String methodName = "openrpc";
    private String openrpcVersion = "1.2.6";
    private List<Server> servers = List.of();
    private Info info = new Info("1.0.0", "JSON-RPC API", null, null, null);
    private List<ErrorValue> globalErrors = List.of(
            new ErrorValue(-32700, "Request deserialization error.", null),
            new ErrorValue(-32603, "Exception message, missing JSON-RPC response.", null),
            new ErrorValue(-32601, "Unknown JSON-RPC method.", null),
            new ErrorValue(-32600, "Invalid request: wrong JSON-RPC version attribute or request JSON type.", null),
            new ErrorValue(-2, "Exception message, unhandled exception", null));

    public OpenRPCEndpoint() {
        super(Impl.class, DefaultScoped.class, 1000, Map.of());
    }

    public OpenRPCEndpoint setGlobalErrors(final List<ErrorValue> globalErrors) {
        this.globalErrors = globalErrors;
        return this;
    }

    public OpenRPCEndpoint setMethodName(final String methodName) {
        this.methodName = methodName;
        return this;
    }

    public OpenRPCEndpoint setOpenrpcVersion(final String openrpcVersion) {
        this.openrpcVersion = openrpcVersion;
        return this;
    }

    public OpenRPCEndpoint setServers(final List<Server> servers) {
        this.servers = servers;
        return this;
    }

    public OpenRPCEndpoint setInfo(final Info info) {
        this.info = info;
        return this;
    }

    @Override
    public OpenRPCEndpoint.Impl create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        return new Impl(container, methodName, openrpcVersion, info, servers, globalErrors);
    }

    public static class Impl implements JsonRpcMethod {
        private volatile CompletableFuture<Map<String, Object>> precomputed;
        private final String name;
        private final Supplier<CompletableFuture<Map<String, Object>>> factory;

        protected Impl(final RuntimeContainer container, final String methodName, final String openrpcVersion,
                       final Info info, final List<Server> servers, final List<ErrorValue> globalErrors) {
            this.name = methodName;
            if (servers.isEmpty()) {
                factory = () -> doCompute(container, openrpcVersion, info, servers, globalErrors);
            } else {
                factory = null;
                precomputed = doCompute(container, openrpcVersion, info, servers, globalErrors);
            }
        }

        private CompletableFuture<Map<String, Object>> doCompute(final RuntimeContainer container, final String openrpcVersion, final Info info, final List<Server> servers, final List<ErrorValue> globalErrors) {
            try (final var mapper = container.lookup(JsonMapper.class)) {
                var usedServers = servers;
                if (usedServers.isEmpty()) {
                    try (final var ws = container.lookup(WebServer.Configuration.class)) {
                        final var configuration = ws.instance();
                        usedServers = List.of(new Server("http://" + configuration.host() + ":" + configuration.port() + "/jsonrpc"));
                    }
                }
                return completedFuture(compute(
                        openrpcVersion, info, usedServers,
                        globalErrors.stream().map(ErrorValue::asMap).toList(),
                        mapper.instance()));
            }
        }

        @SuppressWarnings("unchecked")
        protected Map<String, Object> compute(final String openrpcVersion, final Info info, final List<Server> servers,
                                              final List<Map<String, Object>> globalErrors, final JsonMapper mapper) {
            final var schemas = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            final var methods = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            try {
                final var resources = Thread.currentThread().getContextClassLoader()
                        .getResources("META-INF/fusion/jsonrpc/openrpc.json");
                while (resources.hasMoreElements()) {
                    try (final var in = resources.nextElement().openStream()) {
                        final var partial = (Map<String, Object>) mapper.fromString(Object.class, new String(in.readAllBytes(), UTF_8)
                                // this could/should be moved to rewriting the object tree visiting it but this is easier for now
                                .replace("#/schemas/", "#/components/schemas/"));
                        ofNullable(partial.get("schemas"))
                                .filter(Map.class::isInstance)
                                .map(Map.class::cast)
                                .ifPresent(schemas::putAll);
                        ofNullable(partial.get("methods"))
                                .filter(Map.class::isInstance)
                                .map(Map.class::cast)
                                .ifPresent(methods::putAll);
                    }
                }
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }

            final var out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            final var allMethods = methods.values();
            out.putAll(Map.of(
                    "openrpc", openrpcVersion,
                    "info", info.asMap(),
                    "servers", servers.stream().map(Server::asMap).toList(),
                    "components", Map.of("schemas", schemas),
                    "methods", globalErrors == null || globalErrors.isEmpty() ? allMethods : allMethods.stream()
                            .map(it -> {
                                if (it instanceof Map<?, ?> map) {
                                    final var newMethod = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                                    newMethod.putAll((Map<String, ?>) map);
                                    if (!map.containsKey("errors")) {
                                        newMethod.put("errors", globalErrors);
                                    } else {
                                        final var errors = map.get("errors");
                                        if (errors instanceof List<?> list) {
                                            newMethod.put("errors", Stream.concat(list.stream(), globalErrors.stream()).toList());
                                        }
                                    }
                                    return newMethod;
                                }
                                return it;
                            })
                            .toList()));
            return out;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public CompletionStage<?> invoke(final Context context) {
            if (precomputed == null) { // not a big deal to compute it twice under load, shouldn't occur and worse case will serve the same thing
                precomputed = factory.get();
            }
            return precomputed;
        }
    }

    public record Server(String url) {
        private Map<String, Object> asMap() {
            return Map.of("url", url);
        }
    }

    public record ErrorValue(int code, String message, Object data) {
        private Map<String, Object> asMap() {
            return Stream.of(
                            entry("code", code),
                            message != null ? entry("message", message) : null,
                            data != null ? entry("data", data) : null)
                    .filter(Objects::nonNull)
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        }
    }

    public record Contact(String name, String url, String email) {
        private Map<String, Object> asMap() {
            return Stream.of(
                            name != null ? entry("name", name) : null,
                            url != null ? entry("url", url) : null,
                            email != null ? entry("email", email) : null)
                    .filter(Objects::nonNull)
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        }
    }

    public record License(String name, String url) {
        private Map<String, Object> asMap() {
            return Stream.of(
                            name != null ? entry("name", name) : null,
                            url != null ? entry("url", url) : null)
                    .filter(Objects::nonNull)
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        }
    }

    public record Info(String version, String title, String termsOfService, Contact contact, License license) {
        private Map<String, Object> asMap() {
            return Stream.of(
                            version != null ? entry("version", version) : null,
                            title != null ? entry("title", title) : null,
                            termsOfService != null ? entry("termsOfService", termsOfService) : null,
                            contact != null ? entry("contact", contact.asMap()) : null,
                            license != null ? entry("license", license.asMap()) : null)
                    .filter(Objects::nonNull)
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        }
    }
}
