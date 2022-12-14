package io.yupiik.fusion.http.server.api;

import java.io.IOException;

public interface IOConsumer<A> {
    void accept(final A request) throws IOException;
}
