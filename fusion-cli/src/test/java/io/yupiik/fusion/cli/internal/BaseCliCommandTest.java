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

import io.yupiik.fusion.cli.CliAwaiter;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.configuration.MissingRequiredParameterException;
import io.yupiik.fusion.framework.api.container.DefaultInstance;
import io.yupiik.fusion.framework.api.container.configuration.ConfigurationImpl;
import io.yupiik.fusion.framework.api.main.Args;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BaseCliCommandTest {
    @Test
    void exceptionPropagation() {
        final var msg = "No value for '--name'";
        final var e = assertThrows(MissingRequiredParameterException.class,
                () -> new BaseCliCommand<Map<String, String>, Runnable>(
                        "test",
                        "...",
                        c -> { throw new MissingRequiredParameterException(msg); },
                        (conf, deps) -> { throw new UnsupportedOperationException("shouldn't be called"); },
                        List.of())
                        .create(key -> Optional.empty(), List.of()));
        assertEquals(msg, e.getMessage());
    }

    @Test
    void successfulCreation() {
        try (final var result = new BaseCliCommand<>(
                "test",
                "...",
                c -> Map.of("name", "value"),
                (conf, deps) -> () -> { /* no-op */ },
                List.of())
                .create(key -> Optional.empty(), List.of())) {
            result.instance().run(); // no exception = success
        }
    }

    @Test
    void pathConstructor() {
        final var command = new BaseCliCommand<Map<String, String>, Runnable>(
                new String[]{"deploy", "run"},
                "...",
                c -> Map.of("name", "value"),
                (conf, deps) -> () -> { /* no-op */ },
                List.of());
        assertEquals("deploy/run", command.name());
        assertArrayEquals(new String[]{"deploy", "run"}, command.path());
        assertEquals("--deploy-run-", command.cliPrefix());
    }

    @Test
    void stringConstructorKeepsSingleSegment() {
        final var command = new BaseCliCommand<Map<String, String>, Runnable>(
                "single",
                "...",
                c -> Map.of(),
                (conf, deps) -> () -> { /* no-op */ },
                List.of());
        assertEquals("single", command.name());
        assertArrayEquals(new String[]{"single"}, command.path());
        assertEquals("--single-", command.cliPrefix());
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
