package io.yupiik.fusion.http.server.api;

public interface Unwrappable {
    default <T> T unwrap(final Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        throw new IllegalArgumentException("Can't unwrap " + this + " as '" + type.getName() + "'");
    }
}
