package io.yupiik.fusion.testing.impl;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.testing.Fusion;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.util.ArrayList;
import java.util.List;

import static java.util.Optional.ofNullable;

public class FusionParameterResolver implements ParameterResolver, AfterEachCallback {
    static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(FusionParameterResolver.class);

    @Override
    public boolean supportsParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext) throws ParameterResolutionException {
        final var parameter = parameterContext.getParameter();
        return parameter.isAnnotationPresent(Fusion.class) || parameter.getType() == RuntimeContainer.class;
    }

    @Override
    public Object resolveParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext) throws ParameterResolutionException {
        final var runtimeContainer = extensionContext
                .getStore(NAMESPACE)
                .get(RuntimeContainer.class, RuntimeContainer.class);
        if (parameterContext.getParameter().getType() == RuntimeContainer.class) {
            return runtimeContainer;
        }

        final var lookup = runtimeContainer.lookup(parameterContext.getParameter().getParameterizedType());
        extensionContext.getStore(NAMESPACE).getOrComputeIfAbsent(CleanBag.class, k -> new CleanBag(), CleanBag.class).instances.add(lookup);
        return lookup.instance();
    }

    @Override
    public void afterEach(final ExtensionContext context) {
        ofNullable(context.getStore(NAMESPACE).get(CleanBag.class, CleanBag.class))
                .ifPresent(c -> c.instances.forEach(Instance::close));
    }

    private static class CleanBag {
        private final List<Instance<?>> instances = new ArrayList<>();
    }
}
