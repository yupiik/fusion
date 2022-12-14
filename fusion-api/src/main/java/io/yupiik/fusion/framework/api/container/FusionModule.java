package io.yupiik.fusion.framework.api.container;

import io.yupiik.fusion.framework.api.spi.FusionContext;

import java.util.Map;
import java.util.stream.Stream;

public interface FusionModule {
    default Stream<FusionBean<?>> beans() {
        return Stream.of();
    }

    default Stream<FusionContext> contexts() {
        return Stream.of();
    }

    default Stream<FusionListener<?>> listeners() {
        return Stream.of();
    }

    default Map<String, Object> data() {
        return Map.of();
    }
}
