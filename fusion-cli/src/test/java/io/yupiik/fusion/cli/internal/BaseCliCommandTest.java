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
package io.yupiik.fusion.cli.internal;

import com.sun.jdi.ArrayReference;
import io.yupiik.fusion.cli.CliAwaiter;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.configuration.ConfigurationSource;
import io.yupiik.fusion.framework.api.configuration.MissingRequiredParameterException;
import io.yupiik.fusion.framework.api.container.DefaultInstance;
import io.yupiik.fusion.framework.api.container.configuration.ConfigurationImpl;
import io.yupiik.fusion.framework.api.main.Args;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BaseCliCommandTest {
    @Test
    void errorMessage() {
        assertEquals(
                """
                        No value for '--test-dummy'
                        Available parameters:
                        --test-dummy: Some param.
                        """,
                assertThrows(IllegalArgumentException.class, () -> new BaseCliCommand<Map<String, String>, Runnable>(
                        "test",
                        "...",
                        c -> {
                            // simulate a parameter required=true which is missing
                            throw new MissingRequiredParameterException("No value for 'test.dummy'");
                        },
                        (conf, deps) -> {
                            throw new UnsupportedOperationException("shouldn't be called in this test");
                        },
                        List.of(new CliCommand.Parameter("test.dummy", "--test-dummy", "Some param.")))
                        .create(key -> Optional.empty(), List.of()))
                        .getMessage());
    }

    @Test
    void envOverride() {
        final var param = new AtomicReference<String>();
        final var awaiter = new CliAwaiter(
                new Args(List.of("test")),
                new ConfigurationImpl(List.of(key -> "foo".equals(key) ? "ok" : null)),
                List.of(new CliCommand<>() {
                    @Override
                    public String name() {
                        return "test";
                    }

                    @Override
                    public String description() {
                        return "test";
                    }

                    @Override
                    public List<Parameter> parameters() {
                        return List.of(new Parameter("foo", "foo", "foo"));
                    }

                    @Override
                    public Instance<Runnable> create(final Configuration configuration, final List<Instance<?>> dependents) {
                        return new DefaultInstance<>(
                                null, null,
                                () -> param.set(configuration.get("foo").orElse("failed")),
                                List.of());
                    }
                })
        );
        awaiter.await();
        assertEquals("ok", param.get());
    }
}
