package io.yupiik.fusion.framework.api.container;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.bean.ConfigurationBean;
import io.yupiik.fusion.framework.api.container.bean.NullBean;
import io.yupiik.fusion.framework.api.container.bean.ProvidedInstanceBean;
import io.yupiik.fusion.framework.api.container.context.ApplicationFusionContext;
import io.yupiik.fusion.framework.api.container.context.DefaultFusionContext;
import io.yupiik.fusion.framework.api.container.instance.OptionalInstance;
import io.yupiik.fusion.framework.api.event.Emitter;
import io.yupiik.fusion.framework.api.exception.AmbiguousBeanException;
import io.yupiik.fusion.framework.api.exception.MissingContextException;
import io.yupiik.fusion.framework.api.exception.NoMatchingBeanException;
import io.yupiik.fusion.framework.api.lifecycle.Start;
import io.yupiik.fusion.framework.api.lifecycle.Stop;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.api.spi.FusionContext;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Optional.empty;
import static java.util.stream.Collectors.joining;

public class ContainerImpl implements ConfiguringContainer, RuntimeContainer {
    // runtime state
    private final Beans beans = new Beans();
    private final Contexts contexts = new Contexts();
    private final Listeners listeners = new Listeners();

    private final Types types = new Types();
    private final Map<Type, Type> slowLookupMatchings = new ConcurrentHashMap<>();
    private final Map<Type, List<FusionBean<?>>> listMatchings = new ConcurrentHashMap<>();

    // startup config
    private boolean disableAutoDiscovery = false;
    private ClassLoader loader = Thread.currentThread().getContextClassLoader();

    @Override
    public RuntimeContainer start() {
        if (disableAutoDiscovery) {
            return this;
        }

        final var modules = loadModules().toList();

        // beans
        beans.doRegister(Stream.concat(
                        modules.stream()
                                .flatMap(FusionModule::beans),
                        defaultBeans())
                .toArray(FusionBean<?>[]::new));

        // contexts
        contexts.doRegister(Stream.concat(
                        // default scopes
                        Stream.of(new ApplicationFusionContext(), new DefaultFusionContext()),
                        // discovered ones (through module)
                        modules.stream().flatMap(FusionModule::contexts))
                .toArray(FusionContext[]::new));

        // listeners
        listeners.doRegister(modules.stream().flatMap(FusionModule::listeners)
                .toArray(FusionListener[]::new));

        // startup event
        if (listeners.hasDirectListener(Start.class)) {
            listeners.fire(this, new Start());
        }

        return this;
    }

    @Override
    public ConfiguringContainer disableAutoDiscovery(final boolean disableAutoDiscovery) {
        this.disableAutoDiscovery = disableAutoDiscovery;
        return this;
    }

    @Override
    public ConfiguringContainer loader(final ClassLoader loader) {
        this.loader = loader;
        return this;
    }

    @Override
    public ConfiguringContainer register(final FusionBean<?>... beans) {
        this.beans.doRegister(beans);
        return this;
    }

    @Override
    public ConfiguringContainer register(final FusionListener<?>... listeners) {
        this.listeners.doRegister(listeners);
        return this;
    }

    @Override
    public ConfiguringContainer register(final FusionContext... contexts) {
        this.contexts.doRegister(contexts);
        return this;
    }

    @Override
    public Beans getBeans() {
        return beans;
    }

    @Override
    public Contexts getContexts() {
        return contexts;
    }

    @Override
    public Listeners getListeners() {
        return listeners;
    }

    @Override
    public <T> Instance<T> lookup(final Class<T> type) {
        return lookup((Type) type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Instance<T> lookup(final Type inType) {
        final Type type;
        final boolean optional;
        if (inType instanceof ParameterizedType pt && pt.getRawType() == Optional.class) {
            type = pt.getActualTypeArguments()[0];
            optional = true;
        } else {
            type = inType;
            optional = false;
        }

        final var existing = beans.getBeans().get(type);
        if (existing == null) {
            final var found = slowLookupMatchings.get(type);
            if (found == null) { // slower but wider matching
                final var matching = beans.getBeans().keySet().stream()
                        .filter(it -> types.isAssignable(it, type))
                        .toList();
                if (matching.size() == 1) {
                    final var exactMatching = matching.get(0);
                    slowLookupMatchings.put(type, exactMatching);
                    final var lookup = lookup(exactMatching);
                    return wrapIfNeeded(optional, lookup);
                }
            } else {
                final var lookup = lookup(found);
                return wrapIfNeeded(optional, lookup);
            }

            if (optional) {
                return new DefaultInstance<>(new NullBean<>(type), this, (T) empty(), List.of());
            }
            throw new NoMatchingBeanException("No bean matching type '" + type.getTypeName() + "'");
        }

        if (existing.size() != 1) {
            if (existing.size() > 1) { // enable to override beans using priority (max wins)
                final int max = existing.stream().mapToInt(FusionBean::priority).max().orElse(Integer.MIN_VALUE);
                if (existing.get(1).priority() < max) { // use first bean
                    return doGetInstance(optional, (FusionBean<T>) existing.get(0));
                }
            }

            throw new AmbiguousBeanException("Matching beans for '" + type.getTypeName() + "': " +
                    existing.stream().map(FusionBean::type).map(Type::getTypeName).collect(joining(",", "", ".")));
        }

        return doGetInstance(optional, (FusionBean<T>) existing.get(0));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A, T> Instance<T> lookups(final Class<A> type,
                                      final Function<List<Instance<A>>, T> postProcessor) {
        // note: for now this does not go through scopes so ensure to inject it in a bean with the right scope
        //       to not pay a high lookup cost (app scope for ex)
        var matching = listMatchings.get(type);
        if (matching == null) {
            matching = beans.getBeans().entrySet().stream()
                    .filter(it -> types.isAssignable(it.getKey(), type))
                    .map(Map.Entry::getValue)
                    .flatMap(Collection::stream)
                    .toList();
            listMatchings.putIfAbsent(type, matching);
        }
        final var dependents = matching.stream()
                .map(it -> (Instance<A>) doGetInstance(false, it))
                .toList();
        final var instance = postProcessor.apply(dependents);
        return new DefaultInstance<>(null, this, instance, new ArrayList<>(dependents));
    }

    @Override
    public void close() {
        final var error = new IllegalStateException("Can't close the container properly");

        // stop event
        if (listeners.hasDirectListener(Stop.class)) {
            try {
                listeners.fire(this, new Stop());
            } catch (final RuntimeException re) {
                error.addSuppressed(re);
            }
        }

        // cleanup contexts
        try {
            contexts.close();
        } catch (final RuntimeException re) {
            error.addSuppressed(re);
        }

        if (error.getSuppressed().length > 0) {
            throw error;
        }
    }

    @Override
    public <T> void emit(final T event) {
        listeners.fire(this, event);
    }

    private <T> Instance<T> doGetInstance(final boolean optional, final FusionBean<T> bean) {
        final var instance = contexts.findContext(bean.scope())
                .orElseThrow(() -> new MissingContextException(bean.scope().getName()))
                .getOrCreate(this, bean);
        return wrapIfNeeded(optional, instance);
    }

    protected Stream<FusionModule> loadModules() {
        return ServiceLoader
                .load(FusionModule.class, loader).stream()
                .map(ServiceLoader.Provider::get);
    }

    protected Stream<FusionBean<?>> defaultBeans() {
        return Stream.of(
                new ProvidedInstanceBean<>(ApplicationScoped.class, Emitter.class, () -> this),
                new ProvidedInstanceBean<>(DefaultScoped.class, RuntimeContainer.class, () -> this),
                new ConfigurationBean());
    }

    @SuppressWarnings("unchecked")
    private <T> Instance<T> wrapIfNeeded(final boolean optional, final Instance<?> lookup) {
        if (optional) {
            return (Instance<T>) new OptionalInstance<>(lookup);
        }
        return (Instance<T>) lookup;
    }
}
