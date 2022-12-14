package io.yupiik.fusion.framework.api.configuration;

/**
 * Mark a source of entries.
 * Any bean implementation can be sorted with @{@link io.yupiik.fusion.framework.build.api.order.Order} in default {@link Configuration} implementation.
 */
public interface ConfigurationSource {
    String get(String key);
}
