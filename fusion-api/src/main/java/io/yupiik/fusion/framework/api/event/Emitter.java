package io.yupiik.fusion.framework.api.event;

/**
 * Injectable type to emit container events (see {@link io.yupiik.fusion.framework.build.api.event.OnEvent} and {@link io.yupiik.fusion.framework.api.RuntimeContainer#emit(Object)}.
 */
public interface Emitter {
    <T> void emit(T event);
}
