package io.yupiik.fusion.http.server.impl;

import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.spi.Endpoint;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

public class DefaultEndpoint implements Endpoint {
    private final int priority;
    private final Predicate<Request> matcher;
    private final Function<Request, CompletionStage<Response>> handler;

    public DefaultEndpoint(final int priority, final Predicate<Request> matcher, final Function<Request, CompletionStage<Response>> handler) {
        this.priority = priority;
        this.matcher = matcher;
        this.handler = handler;
    }

    @Override
    public boolean matches(final Request request) {
        return matcher.test(request);
    }

    @Override
    public CompletionStage<Response> handle(final Request request) {
        return handler.apply(request);
    }
}
