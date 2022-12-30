package io.yupiik.fusion.cli.internal;

import io.yupiik.fusion.cli.CliAwaiter;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.main.Args;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CliAwaiterBean extends BaseBean<CliAwaiter> {
    public CliAwaiterBean() {
        super(CliAwaiter.class, DefaultScoped.class, 1000, Map.of());
    }

    @Override
    public CliAwaiter create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        return new CliAwaiter(
                lookup(container, Args.class, dependents),
                lookup(container, Configuration.class, dependents),
                new ArrayList<>(lookups(
                        container, CliCommand.class,
                        l -> l.stream().map(i -> (CliCommand<? extends Runnable>) i.instance()).toList(),
                        dependents)));
    }
}
