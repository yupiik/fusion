package io.yupiik.fusion.framework.api.configuration;

import java.util.Map;
import java.util.Optional;

import static java.util.Optional.ofNullable;

@FunctionalInterface // must stay simple enough to override for custom configuration instantiation
public interface Configuration {
    Optional<String> get(String key);

    static Configuration of(final Map<String, String> conf) {
        return k -> ofNullable(conf.get(k));
    }
}
