package io.yupiik.fusion.framework.api.exception;

public class MissingContextException extends RuntimeException {
    public MissingContextException(final String name) {
        super(name);
    }
}
