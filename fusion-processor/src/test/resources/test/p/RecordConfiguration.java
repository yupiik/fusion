package test.p;

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;

import java.math.BigDecimal;
import java.util.List;

@RootConfiguration("app")
public record RecordConfiguration(
        @Property(documentation = "The app name") String name,
        boolean toggle,
        int age,
        @Property("bigInt") long aLong,
        double number,
        BigDecimal bigNumber,
        NestedConf nested,
        List<NestedConf> nesteds,
        List<String> list) {
}
