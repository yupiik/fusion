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
package io.yupiik.fusion.httpclient.core.listener;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public interface RequestListener<S> {
    default State<S> before(final long count, final HttpRequest request) {
        return new State<>(request, null);
    }

    default void after(final S state, final HttpRequest request, final Throwable error, final HttpResponse<?> response) {
        // no-op
    }

    class State<A> {
        private final HttpRequest request;
        private final A state;

        public State(final HttpRequest request, final A state) {
            this.request = request;
            this.state = state;
        }

        public HttpRequest request() {
            return request;
        }

        public A state() {
            return state;
        }
    }
}
