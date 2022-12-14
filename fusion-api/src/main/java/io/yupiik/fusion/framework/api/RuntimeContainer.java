package io.yupiik.fusion.framework.api;

import io.yupiik.fusion.framework.api.container.Beans;
import io.yupiik.fusion.framework.api.container.Contexts;
import io.yupiik.fusion.framework.api.container.Listeners;
import io.yupiik.fusion.framework.api.event.Emitter;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Function;

public interface RuntimeContainer extends Emitter, AutoCloseable {
    Beans getBeans();

    Contexts getContexts();

    Listeners getListeners();

    <T> Instance<T> lookup(Class<T> type);

    <T> Instance<T> lookup(Type type);

    <A, T> Instance<T> lookups(Class<A> type,
                               Function<List<Instance<A>>, T> postProcessor);

    @Override
    void close();
}
