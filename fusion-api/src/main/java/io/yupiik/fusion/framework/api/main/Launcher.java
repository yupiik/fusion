package io.yupiik.fusion.framework.api.main;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;

/**
 * Default launcher using {@link ConfiguringContainer}/{@code io.yupiik.fusion.framework.api.RuntimeContainer}.
 * It adds the notion of {@link Awaiter} to enable to not stop immediately when started.
 */
public final class Launcher {
    private Launcher() {
        // no-op
    }

    public static void main(final String... args) {
        // todo: forward args to Configuration props
        try (final var container = ConfiguringContainer.of().start();
             final var awaiters = container.lookups(Awaiter.class, list -> list.stream().map(Instance::instance).toList());) {
            awaiters.instance().forEach(Awaiter::await);
        }
    }
}
