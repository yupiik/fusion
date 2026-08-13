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

import io.yupiik.fusion.cli.internal.BaseCliCommand;
import io.yupiik.fusion.cli.internal.CliCommand;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.container.DefaultInstance;
import io.yupiik.fusion.framework.api.main.Args;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CliAwaiterTest {
    private static final Configuration NO_CONFIG = key -> Optional.empty();

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

    @Test
    void subCommandTwoSegments() {
        final var capture = new AtomicReference<String>();
        new CliAwaiter(new Args(List.of("deploy", "run", "--deploy-run-value", "ok")), NO_CONFIG,
                cmds(capturing(capture, "deploy", "run"))).await();
        assertEquals("ok", capture.get());
    }

    @Test
    void subCommandThreeSegments() {
        final var capture = new AtomicReference<String>();
        new CliAwaiter(new Args(List.of("a", "b", "c", "--a-b-c-value", "ok")), NO_CONFIG,
                cmds(capturing(capture, "a", "b", "c"))).await();
        assertEquals("ok", capture.get());
    }

    @Test
    void subCommandFiveSegments() {
        final var capture = new AtomicReference<String>();
        new CliAwaiter(new Args(List.of("a", "b", "c", "d", "e", "--a-b-c-d-e-value", "ok")), NO_CONFIG,
                cmds(capturing(capture, "a", "b", "c", "d", "e"))).await();
        assertEquals("ok", capture.get());
    }

    @Test
    void longestMatchRunsDeepest() {
        final var parent = new AtomicReference<String>();
        final var child = new AtomicReference<String>();
        new CliAwaiter(new Args(List.of("deploy", "run", "--deploy-run-value", "child")), NO_CONFIG,
                cmds(capturing(parent, "deploy"), capturing(child, "deploy", "run"))).await();
        assertNull(parent.get());
        assertEquals("child", child.get());
    }

    @Test
    void longestMatchRunsParentWhenAlone() {
        final var parent = new AtomicReference<String>();
        final var child = new AtomicReference<String>();
        new CliAwaiter(new Args(List.of("deploy", "--deploy-value", "parent")), NO_CONFIG,
                cmds(capturing(parent, "deploy"), capturing(child, "deploy", "run"))).await();
        assertEquals("parent", parent.get());
        assertNull(child.get());
    }

    @Test
    void literalSlashSegmentRunsWithSingleToken() {
        final var capture = new AtomicReference<String>();
        new CliAwaiter(new Args(List.of("deploy/run", "--deploy/run-value", "ok")), NO_CONFIG,
                cmds(capturing(capture, "deploy/run"))).await();
        assertEquals("ok", capture.get());
    }

    @Test
    void literalSlashSegmentDoesNotMatchSplitTokens() {
        final var error = assertThrows(IllegalArgumentException.class,
                () -> new CliAwaiter(new Args(List.of("deploy", "run")), NO_CONFIG,
                        cmds(noop("deploy/run"))).await());
        assertEquals("Missing command 'deploy':\nCommands:\n  deploy/run    desc\n", error.getMessage());
    }

    @Test
    void bareGroupShowsGroupUsage() {
        final var error = assertThrows(IllegalArgumentException.class,
                () -> new CliAwaiter(new Args(List.of("deploy")), NO_CONFIG,
                        cmds(noop("deploy", "run"))).await());
        assertEquals("Commands in 'deploy':\n  deploy/run    desc\n", error.getMessage());
    }

    @Test
    void groupHelpFiltersByGroup() {
        final var error = assertThrows(IllegalArgumentException.class,
                () -> new CliAwaiter(new Args(List.of("deploy", "--help")), NO_CONFIG,
                        cmds(noop("deploy", "run"), noop("other"))).await());
        assertEquals("Commands in 'deploy':\n  deploy/run    desc\n", error.getMessage());
    }

    @Test
    void implicitGroupShowsGroupMarker() {
        final var error = assertThrows(IllegalArgumentException.class,
                () -> new CliAwaiter(new Args(List.of("a", "b")), NO_CONFIG,
                        cmds(noop("a", "b", "c", "d"))).await());
        assertEquals("Commands in 'a/b':\n  a/b/c    (group)\n", error.getMessage());
    }

    @Test
    void unknownSubCommandReportsGroup() {
        final var error = assertThrows(IllegalArgumentException.class,
                () -> new CliAwaiter(new Args(List.of("deploy", "foo")), NO_CONFIG,
                        cmds(noop("deploy", "run"))).await());
        assertEquals("Missing command 'foo' in group 'deploy':\nCommands in 'deploy':\n  deploy/run    desc\n", error.getMessage());
    }

    @Test
    void unknownCommandReportsGlobalUsage() {
        final var error = assertThrows(IllegalArgumentException.class,
                () -> new CliAwaiter(new Args(List.of("unknown")), NO_CONFIG,
                        cmds(noop("deploy", "run"))).await());
        assertEquals("Missing command 'unknown':\nCommands:\n  deploy/run    desc\n", error.getMessage());
    }

    @Test
    void globalHelp() {
        final var commands = cmds(noop("deploy", "run"));
        assertEquals("Commands:\n  deploy/run    desc\n",
                assertThrows(IllegalArgumentException.class,
                        () -> new CliAwaiter(new Args(List.of("--help")), NO_CONFIG, commands).await()).getMessage());
        assertEquals("Commands:\n  deploy/run    desc\n",
                assertThrows(IllegalArgumentException.class,
                        () -> new CliAwaiter(new Args(List.of("help")), NO_CONFIG, commands).await()).getMessage());
    }

    @Test
    void helpPathResolvesCommandAndGroup() {
        final var commands = cmds(capturing(new AtomicReference<>(), "deploy", "run"));
        assertEquals("Commands in 'deploy':\n  deploy/run    desc\n",
                assertThrows(IllegalArgumentException.class,
                        () -> new CliAwaiter(new Args(List.of("help", "deploy")), NO_CONFIG, commands).await()).getMessage());
        assertEquals("Options for 'deploy/run':\n    --value    The value.\n",
                assertThrows(IllegalArgumentException.class,
                        () -> new CliAwaiter(new Args(List.of("help", "deploy", "run")), NO_CONFIG, commands).await()).getMessage());
    }

    @Test
    void leafHelpShowsOptions() {
        final var error = assertThrows(IllegalArgumentException.class,
                () -> new CliAwaiter(new Args(List.of("deploy", "run", "--help")), NO_CONFIG,
                        cmds(capturing(new AtomicReference<>(), "deploy", "run"))).await());
        assertEquals("Options for 'deploy/run':\n    --value    The value.\n", error.getMessage());
    }

    @Test
    void emptyArgs() {
        final var error = assertThrows(IllegalArgumentException.class,
                () -> new CliAwaiter(new Args(List.of()), NO_CONFIG, cmds(noop("deploy", "run"))).await());
        assertEquals("Ensure to call a command:\nCommands:\n  deploy/run    desc\n", error.getMessage());
    }

    @Test
    void singleSegmentStillRuns() {
        assertDoesNotThrow(() ->
                new CliAwaiter(new Args(List.of("deploy")), NO_CONFIG, cmds(noop("deploy"))).await());
    }

    private static CliCommand<? extends Runnable> capturing(final AtomicReference<String> capture, final String... path) {
        final var key = "--" + String.join("-", path) + "-value";
        return new BaseCliCommand<Configuration, Runnable>(path, "desc", c -> c,
                (c, deps) -> () -> capture.set(c.get(key).orElse("missing")),
                List.of(new CliCommand.Parameter("value", key, "The value.")));
    }

    private static CliCommand<? extends Runnable> noop(final String... path) {
        return new BaseCliCommand<>(path, "desc", c -> null, (c, deps) -> () -> { }, List.of());
    }

    @SafeVarargs
    @SuppressWarnings({"unchecked", "varargs"})
    private static List<CliCommand<? extends Runnable>> cmds(final CliCommand<? extends Runnable>... commands) {
        return List.of(commands);
    }
}
