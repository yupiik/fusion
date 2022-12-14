package io.yupiik.fusion.testing.impl;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class FusionMonoLifecycle extends FusionParameterResolver implements BeforeAllCallback {
    private static volatile RuntimeContainer INSTANCE;

    @Override
    public void beforeAll(final ExtensionContext context) {
        if (INSTANCE == null) {
            synchronized (FusionMonoLifecycle.class) {
                if (INSTANCE == null) {
                    INSTANCE = ConfiguringContainer.of().start();
                    Runtime.getRuntime().addShutdownHook(new Thread(INSTANCE::close, getClass().getName() + "-shutdown"));
                }
            }
        }
        context.getStore(NAMESPACE).getOrComputeIfAbsent(RuntimeContainer.class, k -> INSTANCE);
    }
}
