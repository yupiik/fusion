package io.yupiik.fusion.cli.internal;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.configuration.Configuration;

import java.util.List;

public interface CliCommand<C extends Runnable> {
    String name();

    String description();

    Instance<C> create(Configuration configuration, List<Instance<?>> dependents);
}
