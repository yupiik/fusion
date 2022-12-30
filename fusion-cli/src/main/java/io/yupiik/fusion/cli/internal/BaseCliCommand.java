package io.yupiik.fusion.cli.internal;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.container.DefaultInstance;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public class BaseCliCommand<CF, C extends Runnable> implements CliCommand<C> {
    private final String name;
    private final String description;
    private final Function<Configuration, CF> configurationProvider;
    private final BiFunction<CF, List<Instance<?>>, C> constructor;

    public BaseCliCommand(final String name, final String description,
                          final Function<Configuration, CF> configurationProvider,
                          final BiFunction<CF, List<Instance<?>>, C> constructor) {
        this.name = name;
        this.description = description;
        this.configurationProvider = configurationProvider;
        this.constructor = constructor;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public Instance<C> create(final Configuration configuration, final List<Instance<?>> dependents) {
        return new DefaultInstance<>(
                null, null,
                constructor.apply(configurationProvider.apply(configuration), dependents),
                dependents);
    }

    public static class ContainerBaseCliCommand<CF, C extends Runnable> extends BaseCliCommand<CF, C> {
        public ContainerBaseCliCommand(final String name, final String description, final Function<Configuration, CF> configurationProvider,
                                       final BiFunction<CF, List<Instance<?>>, C> constructor) {
            super(name, description, configurationProvider, constructor);
        }

        protected static <T> T lookup(final RuntimeContainer container, final Class<T> type, final List<Instance<?>> deps) {
            final var i = container.lookup(type);
            deps.add(i);
            return i.instance();
        }
    }
}
