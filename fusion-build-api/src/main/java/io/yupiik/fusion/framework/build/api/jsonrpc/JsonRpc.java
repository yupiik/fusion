package io.yupiik.fusion.framework.build.api.jsonrpc;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.SOURCE;

@Target(METHOD)
@Retention(SOURCE)
public @interface JsonRpc {
    /**
     * @return JSON-RPC method name.
     */
    String value();

    /**
     * @return documentation of the method.
     */
    String documentation() default "";
}
