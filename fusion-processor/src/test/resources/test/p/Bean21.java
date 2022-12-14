package test.p;

import io.yupiik.fusion.framework.build.api.scanning.Bean;

@Bean
public class Bean21 implements Comparable<Bean21> {
    @Override
    public String toString() {
        return "bean21";
    }

    @Override
    public int compareTo(final Bean21 other) {
        return toString().compareTo(other.toString());
    }
}
