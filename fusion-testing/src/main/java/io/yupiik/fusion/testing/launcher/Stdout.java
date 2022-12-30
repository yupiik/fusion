package io.yupiik.fusion.testing.launcher;

import java.util.function.Supplier;

public record Stdout(Supplier<String> contentSupplier) {
    public String content() {
        return contentSupplier.get();
    }
}
