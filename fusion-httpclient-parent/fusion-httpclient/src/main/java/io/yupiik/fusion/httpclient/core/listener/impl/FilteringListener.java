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
package io.yupiik.fusion.httpclient.core.listener.impl;

import io.yupiik.fusion.httpclient.core.listener.RequestListener;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toList;

public class FilteringListener<A> implements RequestListener<FilteringListener.State<A>>, AutoCloseable {
    private final State<A> skippedState = new State<>(null, true);

    private final Configuration<A> configuration;

    public FilteringListener(final Configuration<A> configuration) {
        this.configuration = configuration;
    }

    private boolean isSkipped(final HttpRequest request) {
        return configuration.ignoredMethods.contains(request.method()) ||
                configuration.ignoredPaths.stream().anyMatch(it -> it.test(request.uri().getPath()));
    }

    private boolean isSkipped(final Throwable error, final HttpResponse<?> response) {
        if (error != null) {
            return configuration.skipExceptions;
        }
        final int status = response.statusCode();
        return status < configuration.minimumStatus || status > configuration.maximumStatus;
    }

    @Override
    public RequestListener.State<FilteringListener.State<A>> before(final long count, final HttpRequest request) {
        final boolean skip = isSkipped(request);
        if (skip) {
            return new RequestListener.State<>(request, skippedState);
        }
        final var state = configuration.delegate.before(count, request);
        return new RequestListener.State<>(state.request(), new State<>(state, false));
    }

    @Override
    public void after(final State<A> state, final HttpRequest request, final Throwable error, final HttpResponse<?> response) {
        if (!state.skip && !isSkipped(error, response)) {
            configuration.delegate.after(state.value.state(), state.value.request(), error, response);
        }
    }

    @Override
    public void close() throws Exception {
        if (configuration.delegate instanceof AutoCloseable c) {
            c.close();
        }
    }

    public static class Configuration<T> {
        private final RequestListener<T> delegate;

        // request filters
        private List<String> ignoredMethods = List.of();
        private List<Predicate<String>> ignoredPaths = List.of();

        // response filters
        private boolean skipExceptions = false;
        private int minimumStatus = 0;
        private int maximumStatus = 1000;

        public Configuration(final RequestListener<T> delegate) {
            this.delegate = delegate;
        }

        public Configuration<T> setIgnoredPaths(final List<String> ignoredPaths) {
            this.ignoredPaths = ignoredPaths.stream()
                    .map(it -> (it.startsWith("regex:") ?
                            Pattern.compile(it.substring("regex:".length())).asMatchPredicate() :
                            (Predicate<String>) it::equals))
                    .collect(toList());
            return this;
        }

        public Configuration<T> setIgnoredMethods(final List<String> ignoredMethods) {
            this.ignoredMethods = ignoredMethods;
            return this;
        }

        public Configuration<T> setSkipExceptions(final boolean skipExceptions) {
            this.skipExceptions = skipExceptions;
            return this;
        }

        public Configuration<T> setMinimumStatus(final int minimumStatus) {
            this.minimumStatus = minimumStatus;
            return this;
        }

        public Configuration<T> setMaximumStatus(final int maximumStatus) {
            this.maximumStatus = maximumStatus;
            return this;
        }
    }

    public static class State<T> {
        private final RequestListener.State<T> value;
        private final boolean skip;

        private State(final RequestListener.State<T> value, final boolean skip) {
            this.value = value;
            this.skip = skip;
        }
    }
}
