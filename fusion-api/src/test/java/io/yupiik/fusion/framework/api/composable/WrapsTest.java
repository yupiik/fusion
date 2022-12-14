package io.yupiik.fusion.framework.api.composable;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.yupiik.fusion.framework.api.composable.Wraps.wrap;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WrapsTest {
    @Test
    void wrapSupplier() {
        assertEquals(
                "ioc",
                wrap(
                        () -> "c",
                        delegate -> () -> "i" + delegate.get(),
                        delegate -> () -> "o" + delegate.get()));
    }

    @Test
    void wrapRunnable() {
        final var list = new ArrayList<String>();
        wrap(
                () -> {
                    list.add("last");
                },
                delegate -> () -> {
                    list.add("first");
                    return delegate.get();
                },
                delegate -> () -> {
                    list.add("second");
                    return delegate.get();
                });
        assertEquals(List.of("first", "second", "last"), list);
    }
}
