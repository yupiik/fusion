package io.yupiik.fusion.jsonrpc;

import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.impl.DefaultEndpoint;
import io.yupiik.fusion.json.JsonMapper;

import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.logging.Level.SEVERE;

public class JsonRpcEndpoint extends DefaultEndpoint {
    private final Logger logger = Logger.getLogger(getClass().getName());

    private final JsonRpcHandler handler;
    private final JsonMapper mapper;

    public JsonRpcEndpoint(final JsonRpcHandler handler, final JsonMapper mapper, final String path) {
        super(
                1000,
                r -> "POST".equals(r.method()) && path.equals(r.path()),
                null);
        this.handler = handler;
        this.mapper = mapper;
    }

    @Override
    public CompletionStage<Response> handle(final Request request) {
        final CompletionStage<Object> req;
        try { // deserialization error
            req = handler.readRequest(request.body());
        } catch (final RuntimeException ex) {
            return completedFuture(jsonRpcError(-32700, ex));
        }
        // todo: add Before event using the bus to enable security validation -
        //  can be done wrapping the endpoint + overriding (priority) it in the IoC as of today?
        return req
                .thenCompose(in -> handler
                        .execute(in, request)
                        .thenApply(this::response)
                        .exceptionally(ex -> {
                            logger.log(SEVERE, ex, ex::getMessage);
                            return jsonRpcError(-32603, ex);
                        }))
                .exceptionally(error -> jsonRpcError(-32700, error));
    }

    private Response jsonRpcError(final int code, final Throwable error) {
        return response(handler.createResponse(null, code, error.getMessage()));
    }

    private Response response(final Object payload) {
        return Response.of()
                .status(200)
                .header("content-type", "application/json;charset=utf-8")
                .body(mapper.toString(payload))
                .build();
    }
}
