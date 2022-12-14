package test.p;

import io.yupiik.fusion.framework.api.configuration.ConfigurationSource;
import io.yupiik.fusion.framework.build.api.scanning.Bean;

import java.util.Map;

import static java.util.Map.entry;

@Bean
public class TestConf implements ConfigurationSource {
    private final Map<String, String> data = Map.ofEntries(
            entry("app.name", "test"),
            entry("app.toggle", "true"),
            entry("app.age", "123"),
            entry("app.bigInt", "456"),
            entry("app.number", "7.89"),
            entry("app.bigNumber", "10.2"),
            entry("app.list", "ab,cde,fgh"),
            entry("app.nested.nestedValue", "down"),
            entry("app.nested.second.value", "5"),
            entry("app.nesteds.length", "2"),
            entry("app.nesteds.0.nestedValue", "down1"),
            entry("app.nesteds.1.nestedValue", "down2")
    );

    @Override
    public String get(final String key) {
        return data.get(key);
    }
}