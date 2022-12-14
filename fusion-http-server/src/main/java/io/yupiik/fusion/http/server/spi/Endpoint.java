package io.yupiik.fusion.http.server.spi;

import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.impl.DefaultEndpoint;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

public interface Endpoint {
    default int priority() {
        return 1000;
    }

    boolean matches(Request request);

    CompletionStage<Response> handle(Request request);

    /**
     * Factory enabling to instantiate an endpoint in a bean producer method easily.
     *
     * @param matcher  the predicate selecting the endpoint for usage.
     * @param handler  the endpoint implementation.
     * @param priority the endpoint priority.
     * @return the endpoint.
     */
    static Endpoint of(final Predicate<Request> matcher, final Function<Request, CompletionStage<Response>> handler,
                       final int priority) {
        return new DefaultEndpoint(priority, matcher, handler);
    }

    static Endpoint of(final Predicate<Request> matcher, final Function<Request, CompletionStage<Response>> handler) {
        return of(matcher, handler, 1000);
    }
}
