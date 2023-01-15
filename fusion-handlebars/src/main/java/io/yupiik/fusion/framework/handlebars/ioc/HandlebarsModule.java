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
package io.yupiik.fusion.framework.handlebars.ioc;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.handlebars.HandlebarsCompiler;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class HandlebarsModule implements FusionModule {
    @Override
    public Stream<FusionBean<?>> beans() {
        return Stream.of(new HandlebarsCompilerBean());
    }

    private static class HandlebarsCompilerBean extends BaseBean<HandlebarsCompiler> {
        private HandlebarsCompilerBean() {
            super(HandlebarsCompiler.class, ApplicationScoped.class, 1000, Map.of());
        }

        @Override
        public HandlebarsCompiler create(final RuntimeContainer container, final List<Instance<?>> dependents) {
            return new HandlebarsCompiler();
        }
    }
}
