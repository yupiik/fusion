package test.p;

import io.yupiik.fusion.framework.build.api.scanning.Bean;

import java.util.List;

public class GenericProducer {
    @Bean
    public static List<String> list() {
        return List.of("generic", "conf");
    }
}