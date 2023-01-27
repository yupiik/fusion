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
package io.yupiik.fusion.framework.api;

import io.yupiik.fusion.framework.api.container.ConfiguringContainerImpl;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.spi.FusionContext;

public interface ConfiguringContainer {
    static ConfiguringContainer of() {
        return new ConfiguringContainerImpl();
    }

    RuntimeContainer start();

    ConfiguringContainer disableAutoDiscovery(boolean disableAutoDiscovery);

    ConfiguringContainer loader(ClassLoader loader);

    ConfiguringContainer register(FusionModule... modules);

    ConfiguringContainer register(FusionBean<?>... beans);

    ConfiguringContainer register(FusionListener<?>... listeners);

    ConfiguringContainer register(FusionContext... contexts);
}
