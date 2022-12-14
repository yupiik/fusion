package io.yupiik.fusion.http.server.api;

import io.yupiik.fusion.http.server.impl.servlet.ServletCookie;

public interface Cookie extends Unwrappable {
    String name();

    String value();

    String path();

    String domain();

    int maxAge();

    boolean secure();

    boolean httpOnly();

    interface Builder {
        Builder name(String name);

        Builder value(String value);

        Builder path(String path);

        Builder domain(String domain);

        Builder maxAge(int max);

        Builder secure(boolean secure);

        Builder httpOnly(boolean httpOnly);

        default Builder secure() {
            return secure(true);
        }

        default Builder httpOnly() {
            return httpOnly(true);
        }

        Cookie build();
    }

    static Cookie.Builder of() {
        return new ServletCookie.BuilderImpl();
    }
}