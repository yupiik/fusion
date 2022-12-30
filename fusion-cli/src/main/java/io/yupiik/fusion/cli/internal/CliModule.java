package io.yupiik.fusion.cli.internal;

import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionModule;

import java.util.stream.Stream;

public class CliModule implements FusionModule {
    @Override
    public Stream<FusionBean<?>> beans() {
        return Stream.of(new CliAwaiterBean());
    }
}
