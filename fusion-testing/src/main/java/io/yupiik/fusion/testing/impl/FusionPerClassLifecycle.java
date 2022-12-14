package io.yupiik.fusion.testing.impl;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static java.util.Optional.ofNullable;

public class FusionPerClassLifecycle extends FusionParameterResolver implements BeforeAllCallback, AfterAllCallback {
    @Override
    public void beforeAll(final ExtensionContext context) {
        context.getStore(NAMESPACE).getOrComputeIfAbsent(RuntimeContainer.class, k -> ConfiguringContainer.of().start());
    }

    @Override
    public void afterAll(final ExtensionContext context) {
        ofNullable(context.getStore(NAMESPACE).get(RuntimeContainer.class, RuntimeContainer.class))
                .ifPresent(RuntimeContainer::close);
    }
}
