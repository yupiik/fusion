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
package io.yupiik.fusion.cli;

import io.yupiik.fusion.cli.internal.CliCommand;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.container.DefaultInstance;
import io.yupiik.fusion.framework.api.main.Args;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CliAwaiterTest {
    @Test
    void run() {
        final var set = new AtomicReference<String>();
        new CliAwaiter(new Args(List.of("test")), k -> switch (k) {
            case "foo" -> Optional.of("bar");
            default -> Optional.empty();
        }, List.of(new CliCommand<>() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public String description() {
                return "";
            }

            @Override
            public List<Parameter> parameters() {
                return List.of(new Parameter("foo", "foo", ""));
            }

            @Override
            public Instance<Runnable> create(final Configuration configuration, final List<Instance<?>> dependents) {
                return new DefaultInstance<>(null, null, () -> {
                    set.set(configuration.get("foo").orElseThrow());
                }, List.of());
            }
        })).await();
        assertEquals("bar", set.get());
    }
}
