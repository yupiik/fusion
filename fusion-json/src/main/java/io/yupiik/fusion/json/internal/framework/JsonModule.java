package io.yupiik.fusion.json.internal.framework;

import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionModule;

import java.util.stream.Stream;

public class JsonModule implements FusionModule {
    @Override
    public Stream<FusionBean<?>> beans() {
        return Stream.of(new JsonMapperBean());
    }
}
