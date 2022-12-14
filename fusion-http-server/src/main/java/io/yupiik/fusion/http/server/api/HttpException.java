package io.yupiik.fusion.http.server.api;

public class HttpException extends RuntimeException {
    private final Response response;

    public HttpException(final String message, final Throwable parent, final Response response) {
        super(message, parent);
        this.response = response;
    }

    public HttpException(final String message, final Response response) {
        super(message);
        this.response = response;
    }

    public Response getResponse() {
        return response;
    }
}
