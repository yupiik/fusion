package io.yupiik.fusion.framework.build.api.jsonrpc;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.SOURCE;

@Target({})
@Retention(SOURCE)
public @interface JsonRpcError {
    /**
     * @return JSON-RPC error code.
     */
    int code();

    /**
     * @return documentation of the error.
     */
    String documentation() default "";
}
