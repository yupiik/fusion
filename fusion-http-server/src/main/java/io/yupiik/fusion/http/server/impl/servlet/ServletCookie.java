/*
 * Copyright (c) 2022-2023 - Yupiik SAS - https://www.yupiik.com
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
package io.yupiik.fusion.http.server.impl.servlet;

import io.yupiik.fusion.http.server.api.Cookie;

import static java.util.Objects.requireNonNull;

public class ServletCookie implements Cookie {
    private final jakarta.servlet.http.Cookie delegate;

    public ServletCookie(final jakarta.servlet.http.Cookie c) {
        this.delegate = c;
    }

    @Override
    public <T> T unwrap(final Class<T> type) {
        if (jakarta.servlet.http.Cookie.class == type) {
            return type.cast(delegate);
        }
        return Cookie.super.unwrap(type);
    }

    @Override
    public String name() {
        return delegate.getName();
    }

    @Override
    public String value() {
        return delegate.getValue();
    }

    @Override
    public String path() {
        return delegate.getPath();
    }

    @Override
    public String domain() {
        return delegate.getDomain();
    }

    @Override
    public int maxAge() {
        return delegate.getMaxAge();
    }

    @Override
    public boolean secure() {
        return delegate.getSecure();
    }

    @Override
    public boolean httpOnly() {
        return delegate.isHttpOnly();
    }

    public static class BuilderImpl implements Cookie.Builder {
        private String name;
        private String value;
        private String path;
        private String domain;
        private Integer maxAge;
        private Boolean secure;
        private Boolean httpOnly;

        @Override
        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        @Override
        public Builder value(final String value) {
            this.value = value;
            return this;
        }

        @Override
        public Builder path(final String path) {
            this.path = path;
            return this;
        }

        @Override
        public Builder domain(final String domain) {
            this.domain = domain;
            return this;
        }

        @Override
        public Builder maxAge(final int max) {
            this.maxAge = max;
            return this;
        }

        @Override
        public Builder secure(final boolean secure) {
            this.secure = secure;
            return this;
        }

        @Override
        public Builder httpOnly(final boolean httpOnly) {
            this.httpOnly = httpOnly;
            return this;
        }

        @Override
        public Cookie build() {
            final var impl = new jakarta.servlet.http.Cookie(
                    requireNonNull(name, "Cookie name required"),
                    requireNonNull(value, "Cookie value required"));
            if (path != null) {
                impl.setPath(path);
            }
            if (domain != null) {
                impl.setDomain(domain);
            }
            if (maxAge != null) {
                impl.setMaxAge(maxAge);
            }
            if (secure != null) {
                impl.setSecure(secure);
            }
            if (httpOnly != null) {
                impl.setHttpOnly(httpOnly);
            }
            return new ServletCookie(impl);
        }
    }
}
