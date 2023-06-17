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
package io.yupiik.fusion.jwt.beans;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.container.context.subclass.DelegatingContext;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.jwt.Jwt;
import io.yupiik.fusion.jwt.JwtValidatorConfiguration;
import io.yupiik.fusion.jwt.JwtValidatorFactory;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.util.Optional.empty;

public class JwtValidatorFactoryBean extends BaseBean<JwtValidatorFactory> {
    public JwtValidatorFactoryBean() {
        super(JwtValidatorFactory.class, ApplicationScoped.class, 1_000, Map.of(
                "fusion.framework.subclasses.delegate", (Function<DelegatingContext<JwtValidatorFactory>, JwtValidatorFactory>) JwtValidatorFactorySubClass::new));
    }

    @Override
    @SuppressWarnings("unchecked")
    public JwtValidatorFactory create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        final Optional<Clock> clock = (Optional<Clock>) lookup(container, new Types.ParameterizedTypeImpl(Optional.class, Clock.class), dependents);
        final Optional<JsonMapper> mapper = (Optional<JsonMapper>) lookup(container, new Types.ParameterizedTypeImpl(Optional.class, JsonMapper.class), dependents);
        return new JwtValidatorFactory(
                mapper.orElseGet(() -> new JsonMapperImpl(List.of(), c -> empty())),
                clock.orElseGet(Clock::systemUTC));
    }

    private static class JwtValidatorFactorySubClass extends JwtValidatorFactory {
        private final DelegatingContext<JwtValidatorFactory> context;

        private JwtValidatorFactorySubClass(final DelegatingContext<JwtValidatorFactory> ctx) {
            super(null, null);
            context = ctx;
        }

        @Override
        public Function<String, Jwt> newValidator(final JwtValidatorConfiguration configuration) {
            return context.instance().newValidator(configuration);
        }
    }
}
