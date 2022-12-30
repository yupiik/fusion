package io.yupiik.fusion.framework.api.main;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;

import java.util.List;
import java.util.Map;

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
        try (final var container = ConfiguringContainer.of()
                .register(new BaseBean<Args>(Args.class, DefaultScoped.class, 1000, Map.of()) {
                    @Override
                    public Args create(final RuntimeContainer container, final List<Instance<?>> dependents) {
                        return new Args(List.of(args));
                    }
                })
                .start();
             final var awaiters = container.lookups(Awaiter.class, list -> list.stream().map(Instance::instance).toList());) {
            awaiters.instance().forEach(Awaiter::await);
        }
    }
}
