package io.yupiik.fusion.framework.api.container;

import io.yupiik.fusion.framework.api.RuntimeContainer;

import java.lang.reflect.Type;

public interface FusionListener<E> {
    Type eventType();

    void onEvent(RuntimeContainer container, E event);

    default int priority() {
        return 1000;
    }
}
