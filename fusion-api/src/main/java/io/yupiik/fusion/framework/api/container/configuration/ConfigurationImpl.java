package io.yupiik.fusion.framework.api.container.configuration;

import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.configuration.ConfigurationSource;
import io.yupiik.fusion.framework.api.container.configuration.source.EnvironmentSource;
import io.yupiik.fusion.framework.api.container.configuration.source.SystemPropertiesSource;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class ConfigurationImpl implements Configuration {
    private final List<ConfigurationSource> sources;

    public ConfigurationImpl(final List<ConfigurationSource> sources) {
        this.sources = Stream.concat(
                        sources.stream(),
                        Stream.of(new SystemPropertiesSource(), new EnvironmentSource()))
                .toList();
    }

    @Override
    public Optional<String> get(final String key) {
        return sources.stream()
                .map(s -> s.get(key))
                .filter(Objects::nonNull)
                .findFirst();
    }
}
