package io.yupiik.fusion.framework.api.container.configuration.source;

import io.yupiik.fusion.framework.api.configuration.ConfigurationSource;

public class SystemPropertiesSource implements ConfigurationSource {
    @Override
    public String get(final String key) {
        return System.getProperty(key);
    }
}
