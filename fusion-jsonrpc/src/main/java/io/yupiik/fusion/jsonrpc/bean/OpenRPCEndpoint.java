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
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Map.entry;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toMap;

public class OpenRPCEndpoint extends BaseBean<OpenRPCEndpoint.Impl> implements FusionBean<OpenRPCEndpoint.Impl> {
    private String methodName = "openrpc";
    private String openrpcVersion = "1.2.6";
    private List<Server> servers = List.of();
    private Info info = new Info("1.0.0", "JSON-RPC API", null, null, null);

    public OpenRPCEndpoint() {
        super(Impl.class, DefaultScoped.class, 1000, Map.of());
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
        return new Impl(container, methodName, openrpcVersion, info, servers);
    }

    protected static class Impl implements JsonRpcMethod {
        private final CompletableFuture<String> precomputed;
        private final String name;

        protected Impl(final RuntimeContainer container, final String methodName, final String openrpcVersion,
                       final Info info, final List<Server> servers) {
            this.name = methodName;
            try (final var mapper = container.lookup(JsonMapper.class)) {
                var usedServers = servers;
                if (usedServers.isEmpty()) {
                    try (final var ws = container.lookup(WebServer.Configuration.class)) {
                        final var configuration = ws.instance();
                        usedServers = List.of(new Server("http://" + configuration.host() + ":" + configuration.port() + "/jsonrpc"));
                    }
                }
                precomputed = completedFuture(compute(openrpcVersion, info, usedServers, mapper.instance()));
            }
        }

        @SuppressWarnings("unchecked")
        protected String compute(final String openrpcVersion, final Info info, final List<Server> servers, final JsonMapper mapper) {
            final var schemas = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            final var methods = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            try {
                final var resources = Thread.currentThread().getContextClassLoader()
                        .getResources("META-INF/fusion/jsonrpc/openrpc.json");
                while (resources.hasMoreElements()) {
                    try (final var in = resources.nextElement().openStream()) {
                        final var partial = (Map<String, Object>) mapper.fromString(Object.class, new String(in.readAllBytes(), UTF_8)
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
            out.putAll(Map.of(
                    "openrpc", openrpcVersion,
                    "info", info.asMap(),
                    "servers", servers.stream().map(Server::asMap).toList(),
                    "components", Map.of("schemas", schemas),
                    "methods", methods.values()));
            return mapper.toString(out);
        }

        @Override
        public String name() {
            return "openrpc";
        }

        @Override
        public CompletionStage<?> invoke(final Context context) {
            return precomputed;
        }
    }

    public record Server(String url) {
        private Map<String, Object> asMap() {
            return Map.of("url", url);
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
