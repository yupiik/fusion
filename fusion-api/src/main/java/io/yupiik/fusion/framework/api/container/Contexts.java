package io.yupiik.fusion.framework.api.container;

import io.yupiik.fusion.framework.api.spi.FusionContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

public class Contexts implements AutoCloseable {
    private final Map<Class<?>, Optional<FusionContext>> contexts = new HashMap<>();

    public Map<Class<?>, Optional<FusionContext>> getContexts() {
        return contexts;
    }

    public Optional<FusionContext> findContext(final Class<?> marker) {
        final var fusionContext = contexts.get(marker);
        if (fusionContext == null) {
            return Optional.empty();
        }
        return fusionContext;
    }

    public void doRegister(final FusionContext... contexts) {
        this.contexts.putAll(Stream.of(contexts).collect(toMap(FusionContext::marker, Optional::of)));
    }

    @Override
    public void close() {
        final var error = new IllegalStateException("Can't close the contexts properly");
        contexts.values().stream()
                .map(Optional::orElseThrow)
                .filter(AutoCloseable.class::isInstance)
                .map(AutoCloseable.class::cast)
                .sorted(comparing(it -> it.getClass().getName())) // be deterministic
                .forEach(c -> {
                    try {
                        c.close();
                    } catch (final Exception e) {
                        error.addSuppressed(e);
                    }
                });
        if (error.getSuppressed().length > 0) {
            throw error;
        }
    }
}
