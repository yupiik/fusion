package io.yupiik.fusion.framework.build.api.http;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import static io.yupiik.fusion.framework.build.api.http.HttpMatcher.PathMatching.IGNORED;

/**
 * Mark a method taking as input parameter a {@code io.yupiik.fusion.http.server.api.Request} and returning a
 * {@code java.util.concurrent.CompletionStage<io.yupiik.fusion.http.server.api.Response>}.
 * <p>
 * When matcher condition are met the endpoint will be called.
 */
@Target(METHOD)
@Retention(SOURCE)
public @interface HttpMatcher {
    /**
     * Endpoint priority.
     * Enables to use a fallback endpoint by matching all methods and paths (or a subpart) and use it only if none of the other endpoints match.
     *
     * @return endpoint priority
     */
    int priority() default 1000;

    /**
     * @return the HTTP method(s) to match, empty means all.
     */
    String[] methods() default {};

    /**
     * @return how to match the path.
     */
    PathMatching pathMatching() default IGNORED;

    /**
     * @return the path to match respecting {@link #pathMatching()}  matching.
     */
    String path() default "";

    enum PathMatching {
        /**
         * Path is not matched - ignored.
         */
        IGNORED,

        /**
         * Path is exactly the configured value.
         */
        EXACT,

        /**
         * Path starts with the configured value.
         */
        STARTS_WITH,

        /**
         * Path ends with the configured value.
         */
        ENDS_WITH,

        /**
         * Path matches the configured regex.
         * Important: take care your regex is not vulnerable to attacks (take care to wildcards).
         * <p>
         * If used, a {@link java.util.regex.Pattern} will be set in request attribute
         * {@code fusion.http.matcher}.
         */
        REGEX
    }
}
