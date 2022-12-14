package test.p;

import io.yupiik.fusion.framework.build.api.configuration.Property;

public record NestedConf(@Property(documentation = "The nested main value.") String nestedValue, Nest2 second) {
    public record Nest2(@Property(documentation = "Some int.") int value) {
    }
}