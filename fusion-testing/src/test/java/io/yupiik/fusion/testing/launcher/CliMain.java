package io.yupiik.fusion.testing.launcher;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.main.Args;
import io.yupiik.fusion.framework.api.main.Awaiter;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@DefaultScoped
public class CliMain implements Awaiter {
    private final Args args;

    public CliMain(final Args args) {
        this.args = args;
    }

    @Override
    public void await() {
        System.out.println("Args=" + args);
    }

    public static class Module implements FusionModule {
        @Override
        public Stream<FusionBean<?>> beans() {
            return Stream.of(new BaseBean<CliMain>(CliMain.class, DefaultScoped.class, 1000, Map.of()) {
                @Override
                public CliMain create(final RuntimeContainer container, final List<Instance<?>> dependents) {
                    final var instance = container.lookup(Args.class);
                    dependents.add(instance);
                    return new CliMain(instance.instance());
                }
            });
        }
    }
}
