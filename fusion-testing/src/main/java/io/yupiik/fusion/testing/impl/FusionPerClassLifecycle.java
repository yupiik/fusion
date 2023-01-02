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
package io.yupiik.fusion.testing.impl;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static java.util.Optional.ofNullable;

public class FusionPerClassLifecycle extends FusionParameterResolver implements BeforeAllCallback, AfterAllCallback {
    @Override
    public void beforeAll(final ExtensionContext context) {
        context.getStore(NAMESPACE).getOrComputeIfAbsent(RuntimeContainer.class, k -> ConfiguringContainer.of().start());
    }

    @Override
    public void afterAll(final ExtensionContext context) {
        ofNullable(context.getStore(NAMESPACE).get(RuntimeContainer.class, RuntimeContainer.class))
                .ifPresent(RuntimeContainer::close);
    }
}
