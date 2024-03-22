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
package io.yupiik.fusion.testing.http.internal;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.testing.http.TestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class TestClientBean extends BaseBean<TestClient> {
    public TestClientBean() {
        super(TestClient.class, ApplicationScoped.class, 1_000, Map.of());
    }

    @Override
    public TestClient create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        final var json = container.lookup(JsonMapper.class);
        dependents.add(json);
        return new TestClient(TestClient.HttpClients.create(), json.instance(), findUri(container));
    }

    @Override
    public void destroy(final RuntimeContainer container, final TestClient instance) {
        instance.close();
    }

    private URI findUri(final RuntimeContainer container) {
        try (final var conf = container.lookup(Configuration.class)) {
            return conf.instance().get("fusion.testing")
                    .filter(Predicate.not(String::isBlank))
                    .map(URI::create)
                    .orElseGet(() -> {
                        try (final var web = container.lookup(WebServer.Configuration.class)) {
                            return URI.create("http://localhost:" + web.instance().port());
                        }
                    });
        }
    }
}
