package io.yupiik.fusion.framework.api.configuration;

import java.util.Optional;

public interface Configuration {
    Optional<String> get(String key);
}
