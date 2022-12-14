package test.p;

import io.yupiik.fusion.framework.build.api.scanning.Bean;

@Bean
public class NotABeanBeanImpl extends NotABean {
    @Override
    public String toString() {
        return "NotABeanBeanImpl[]";
    }
}