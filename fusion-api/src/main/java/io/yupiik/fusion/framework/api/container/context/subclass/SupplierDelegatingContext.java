package io.yupiik.fusion.framework.api.container.context.subclass;

import java.util.function.Supplier;

public class SupplierDelegatingContext<T> implements DelegatingContext<T> {
    private final Supplier<T> supplier;

    public SupplierDelegatingContext(final Supplier<T> supplier) {
        this.supplier = supplier;
    }

    @Override
    public T instance() {
        return supplier.get();
    }
}
