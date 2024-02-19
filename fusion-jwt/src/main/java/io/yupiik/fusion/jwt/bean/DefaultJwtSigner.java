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
package io.yupiik.fusion.jwt.bean;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.jwt.JwtSignerConfiguration;
import io.yupiik.fusion.jwt.JwtSignerFactory;

import java.util.Map;
import java.util.function.Function;

@ApplicationScoped
public class DefaultJwtSigner implements Function<Map<String, Object>, String> {
    private final Function<Map<String, Object>, String> delegate;

    public DefaultJwtSigner(final JwtSignerFactory factory, final JwtSignerConfiguration configuration) {
        this.delegate = factory == null ? null : factory.newJwtFactory(configuration);
    }

    @Override
    public String apply(final Map<String, Object> value) {
        return delegate.apply(value);
    }
}
