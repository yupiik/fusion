/*
 * Copyright (c) 2022 - present - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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
