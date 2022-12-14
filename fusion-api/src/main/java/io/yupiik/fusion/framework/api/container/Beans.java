package io.yupiik.fusion.framework.api.container;

import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class Beans {
    private final Map<Type, List<FusionBean<?>>> beans = new HashMap<>();

    public Map<Type, List<FusionBean<?>>> getBeans() {
        return beans;
    }

    public void doRegister(final FusionBean<?>... beans) {
        this.beans.putAll(Stream.of(beans)
                .collect(groupingBy(
                        FusionBean::type,
                        collectingAndThen(toList(), l -> l.stream()
                                .sorted(Comparator.<FusionBean<?>, Integer>comparing(FusionBean::priority).reversed())
                                .toList()))));
    }
}
