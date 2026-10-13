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
package io.yupiik.fusion.http.server.impl.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.http.server.api.WebServer;

import java.util.List;
import java.util.Map;

public class FusionServerBean extends BaseBean<WebServer> {
    public FusionServerBean() {
        super(WebServer.class, ApplicationScoped.class, 1000, Map.of());
    }

    @Override
    public WebServer create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        return WebServer.of(lookup(container, WebServer.Configuration.class, dependents));
    }

    @Override
    public void destroy(final RuntimeContainer container, final WebServer instance) {
        instance.close();
    }
}
