package io.yupiik.fusion.framework.api.container.configuration.source;

import io.yupiik.fusion.framework.api.configuration.ConfigurationSource;

import java.util.regex.Pattern;

import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;

public class EnvironmentSource implements ConfigurationSource {
    private final Pattern posix = Pattern.compile("[^A-Za-z0-9]");

    @Override
    public String get(final String key) {
        return ofNullable(System.getenv(key))
                .or(() -> {
                    final var posixKey = posix.matcher(key).replaceAll("_");
                    return ofNullable(System.getenv(posixKey))
                            .or(() -> ofNullable(System.getenv(posixKey.toUpperCase(ROOT))));
                })
                .orElse(null);
    }
}
