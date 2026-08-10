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
package io.yupiik.fusion.json.internal.framework;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.json.schema.JsonSchemaService;

import java.lang.reflect.Type;
import java.util.List;

public class JsonSchemaServiceBean implements FusionBean<JsonSchemaService> {
    @Override
    public Type type() {
        return JsonSchemaService.class;
    }

    @Override
    public Class<?> scope() {
        return ApplicationScoped.class;
    }

    @Override
    public JsonSchemaService create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        return new JsonSchemaService();
    }

    @Override
    public void destroy(final RuntimeContainer container, final JsonSchemaService instance) {
        // no-op, stateless
    }
}