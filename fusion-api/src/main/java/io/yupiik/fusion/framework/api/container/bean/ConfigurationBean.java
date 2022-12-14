package io.yupiik.fusion.framework.api.container.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.configuration.ConfigurationSource;
import io.yupiik.fusion.framework.api.container.configuration.ConfigurationImpl;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;

import java.util.List;
import java.util.Map;

import static java.util.Comparator.comparing;

public class ConfigurationBean extends BaseBean<Configuration> {
    public ConfigurationBean() {
        super(Configuration.class, ApplicationScoped.class, 1000, Map.of());
    }

    @Override
    public Configuration create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        return new ConfigurationImpl(lookups(
                container, ConfigurationSource.class,
                i -> i.stream()
                        .sorted(comparing(inst -> inst.bean().priority()))
                        .map(Instance::instance)
                        .toList(),
                dependents));
    }
}
