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
package io.yupiik.fusion.framework.api.container.context;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.DefaultInstance;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.api.spi.FusionContext;

import java.util.ArrayList;
import java.util.Collections;

public class DefaultFusionContext implements FusionContext {
    @Override
    public Class<?> marker() {
        return DefaultScoped.class;
    }

    @Override
    public <T> Instance<T> getOrCreate(final RuntimeContainer container, final FusionBean<T> bean) {
        final var dependents = new ArrayList<Instance<?>>();
        final var instance = bean.create(container, dependents);
        Collections.reverse(dependents); // destroy in reverse order
        return new DefaultInstance<>(bean, container, instance, dependents);
    }
}
