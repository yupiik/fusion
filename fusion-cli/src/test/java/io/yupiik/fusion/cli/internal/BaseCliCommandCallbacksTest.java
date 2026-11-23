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
import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.configuration.MissingRequiredParameterException;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.main.Args;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseCliCommandCallbacksTest {
    @Test
    void descriptorsKeepPerInvocationLookupRunAndClose() {
        final var events = new ArrayList<String>();
        try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true).register(new DependencyBean(events)).start()) {
            final var first = command(4, new String[]{"deploy", "run"}, container);
            final var second = command(6, new String[]{"literal/name"}, container);
            assertEquals(first.getClass(), second.getClass());
            assertEquals("deploy/run", first.name());
            assertArrayEquals(new String[]{"deploy", "run"}, first.path());
            assertArrayEquals(new String[]{"literal/name"}, second.path());
            assertEquals("Description 4", first.description());
            assertEquals(Map.of("id", "4"), first.metadata());
            assertEquals("documented", first.parameters().get(0).description());
            assertTrue(events.isEmpty(), "Creating descriptors must not look up runnable dependencies");

            final Configuration config = key -> Optional.of("value");
            final var aDeps = new ArrayList<Instance<?>>();
            final var bDeps = new ArrayList<Instance<?>>();
            try (final var a = first.create(config, aDeps); final var b = first.create(config, bDeps)) {
                assertNotSame(a.instance(), b.instance());
                assertNotSame(aDeps.get(0), bDeps.get(0));
                assertEquals(List.of("lookup", "lookup"), events);
                a.instance().run();
                b.instance().run();
                assertEquals(List.of("lookup", "lookup", "run:4:value", "run:4:value"), events);
            }
            assertEquals(List.of("lookup", "lookup", "run:4:value", "run:4:value", "close", "close"), events);
            assertTrue(aDeps.isEmpty());
            assertTrue(bDeps.isEmpty());
            try (final var c = second.create(config, new ArrayList<>())) {
                c.instance().run();
            }
            assertEquals(List.of("lookup", "run:6:value", "close"), events.subList(6, 9));
        }
    }

    @Test
    void explicitKeyMapperIsExposedAndPassedToConfigurationBindingWithHelp() {
        final Function<String, String> mapper = BaseCliCommandCallbacksTest::mapKey;
        final var command = new BaseCliCommand<String, Runnable>(new String[]{"configured"}, "description",
                List.of(), Map.of(), "\nConfigured help\n", mapper, Callbacks::configure, (config, dependents) -> () -> {});
        assertSame(mapper, command.keyMapper());
        assertEquals("--deploy-run-name", command.keyMapper().apply("model.name"));
        assertEquals("--other-key", command.keyMapper().apply("other.key"));
        final var error = assertThrows(MissingRequiredParameterException.class,
                () -> command.create(key -> Optional.empty(), new ArrayList<>()));
        assertEquals("No value for '--deploy-run-name'\nConfigured help\n", error.getMessage());
    }

    @Test
    void fullConstructorSupportsCommandsWithoutConfiguration() {
        final var command = new BaseCliCommand<Object, Runnable>(new String[]{"no-config"}, "description",
                List.of(), Map.of(), "", Function.identity(), null, (config, dependents) -> {
                    assertNull(config);
                    return () -> {};
                });
        try (final var instance = command.create(key -> Optional.empty(), new ArrayList<>())) {
            instance.instance().run();
        }
    }

    @Test
    void awaiterUsesMappedOptionsAndShortNamesAndClosesDependencies() {
        final var events = new ArrayList<String>();
        try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true).register(new DependencyBean(events)).start()) {
            final var command = command(4, new String[]{"deploy", "run"}, container);
            final Configuration empty = key -> Optional.empty();
            new CliAwaiter(new Args(List.of("deploy", "run", "--name", "short")), empty, List.of(command)).await();
            new CliAwaiter(new Args(List.of("deploy", "run", "--deploy-run-name", "full")), empty, List.of(command)).await();
            assertEquals(List.of("lookup", "run:4:short", "close", "lookup", "run:4:full", "close"), events);
            final var usage = new CliAwaiter(new Args(List.of()), empty, List.of(command)).usage();
            assertTrue(usage.contains("Description 4"));
            assertTrue(usage.contains("--name"));
            assertTrue(usage.contains("documented"));
        }
    }

    private static BaseCliCommand<String, Runnable> command(final int id,
                                                            final String[] path, final RuntimeContainer container) {
        return new BaseCliCommand<>(path, "Description " + id,
                List.of(new CliCommand.Parameter("model.name", "--deploy-run-name", "documented")),
                Map.of("id", Integer.toString(id)), "\nHelp " + id, BaseCliCommandCallbacksTest::mapKey, Callbacks::configure,
                (config, dependents) -> Callbacks.createCommand(id, config, container, dependents));
    }

    private static String mapKey(final String key) {
        return "model.name".equals(key) ? "--deploy-run-name" : "--" + key.replace('.', '-');
    }

    private static final class Callbacks {
        private static String configure(final Configuration configuration, final Function<String, String> keyMapper) {
            final var value = configuration.get("model.name");
            if (value.isEmpty()) {
                throw new MissingRequiredParameterException("No value for '" + keyMapper.apply("model.name") + "'");
            }
            return value.get();
        }


        private static Runnable createCommand(final int id, final String configuration, final RuntimeContainer container,
                                               final List<Instance<?>> dependents) {
            final var dependency = container.lookup(Dependency.class);
            dependents.add(dependency);
            return new Command(id, configuration, dependency.instance());
        }
    }

    private record Dependency(List<String> events) {
    }

    private record Command(int id, String configuration, Dependency dependency) implements Runnable {
        @Override
        public void run() {
            dependency.events().add("run:" + id + ":" + configuration);
        }
    }

    private static final class DependencyBean extends BaseBean<Dependency> {
        private final List<String> events;

        private DependencyBean(final List<String> events) {
            super(Dependency.class, DefaultScoped.class, 1000, Map.of());
            this.events = events;
        }

        @Override
        public Dependency create(final RuntimeContainer container, final List<Instance<?>> dependents) {
            events.add("lookup");
            return new Dependency(events);
        }

        @Override
        public void destroy(final RuntimeContainer container, final Dependency instance) {
            events.add("close");
        }
    }
}
