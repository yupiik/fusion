package test.p;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.scanning.Bean;

@ApplicationScoped
public class MethodProducerWithInjections {
    @Bean
    public String create(final Bean2 bean2) {
        return '>' + bean2.toString() + '<';
    }
}