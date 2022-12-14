package test.p;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.scanning.Injection;

@ApplicationScoped
public class Bean1 {
    @Injection
    Bean2 bean2;

    @Override
    public String toString() {
        return "bean1[" + bean2 + "]";
    }
}
