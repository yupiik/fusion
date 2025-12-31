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

/**
 * Mark a method taking as input parameter a {@code io.yupiik.fusion.http.server.api.Request} and returning a
 * {@code java.util.concurrent.CompletionStage<io.yupiik.fusion.http.server.api.Response>}.
 * <p>
 * When matcher condition are met the endpoint will be called.
 * <p>
 * Compares to {@link HttpMatcher} it does inject the matcher logic as plain java code.
 */
@Target(METHOD)
@Retention(SOURCE)
public @interface HttpJavaMatcher {
    /**
     * Endpoint priority.
     * Enables to use a fallback endpoint by matching all methods and paths (or a subpart) and use it only if none of the other endpoints match.
     *
     * @return endpoint priority
     */
    int priority() default 1000;

    /**
     * IMPORTANT: this is the body of a lambda, while it can be inlined (<code>req.path().startsWith("/foo")</code>),
     *            it can need to be surrounded with braces if it uses multiple instructions and/or <code>return</code> keyword
     *            (<code>{ return req.path().startsWith("/foo"); }</code>).
     *            Type of the <code>req</code> instance is <code>io.yupiik.fusion.http.server.api.Request</code>.
     * @return the java logic to evaluate if the request matches. The implicit instance is called <code>req</code>.
     */
    String value() default "true";
}
