/*
 * Copyright (c) 2022-2023 - Yupiik SAS - https://www.yupiik.com
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

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.internal.framework.JsonMapperBean;
import io.yupiik.fusion.json.pretty.PrettyJsonMapper;
import io.yupiik.fusion.jsonrpc.bean.OpenRPCEndpoint;
import io.yupiik.fusion.jsonrpc.impl.JsonRpcMethod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public class OpenRpcGenerator implements Runnable {
    private final Map<String, String> configuration;

    public OpenRpcGenerator(final Map<String, String> configuration) {
        this.configuration = configuration;
    }

    @Override
    public void run() {
        final var target = Path.of(requireNonNull(configuration.get("output"), "No output configuration"));
        try (final var container = ConfiguringContainer.of()
                .disableAutoDiscovery(true)
                .register(new JsonMapperBean(), new OpenRPCEndpoint())
                .start();
             final var mapper = container.lookup(JsonMapper.class).instance()) {
            final var mapperInstance = new PrettyJsonMapper(mapper);
            final var endpoint = new OpenRPCEndpoint()
                    .setServers(List.of(new OpenRPCEndpoint.Server(configuration.getOrDefault("api", "http://localhost:8080"))))
                    .setInfo(new OpenRPCEndpoint.Info(
                            configuration.getOrDefault("version", "1.2.6"),
                            configuration.getOrDefault("title", "OpenRPC"),
                            configuration.get("termsOfService"),
                            ofNullable(configuration.get("contactName"))
                                    .map(c -> new OpenRPCEndpoint.Contact(c, configuration.get("contactName"), configuration.get("contactEmail")))
                                    .orElse(null),
                            ofNullable(configuration.get("licenseName"))
                                    .map(l -> new OpenRPCEndpoint.License(l, configuration.get("licenseUrl")))
                                    .orElse(null)))
                    .create(container, new ArrayList<>());
            final var openrpc = mapperInstance.toString(endpoint
                    .invoke(new JsonRpcMethod.Context(null, null))
                    .toCompletableFuture().get());

            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, mapperInstance.toString(mapperInstance.fromString(Object.class, openrpc)));
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
