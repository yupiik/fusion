package io.yupiik.fusion.cli.internal;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.configuration.Configuration;

import java.util.List;

public interface CliCommand<C extends Runnable> {
    String name();

    String description();

    List<Parameter> parameters();

    Instance<C> create(Configuration configuration, List<Instance<?>> dependents);

    record Parameter(String configName, String cliName, String description) {}
}
