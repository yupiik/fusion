package test.p;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.lifecycle.Destroy;
import io.yupiik.fusion.framework.build.api.lifecycle.Init;
import io.yupiik.fusion.framework.build.api.scanning.Injection;

@ApplicationScoped
public class Lifecycled extends LifecycledDep {
    @Injection
    LifecycledDep bean2;

    @Init
    @Override
    protected void init() {
        if (bean2 != null) {
            super.init();
        }
    }

    @Destroy
    @Override
    protected void destroy() {
        if (bean2 != null) {
            super.destroy();
        }
    }

    @Override
    public String toString() {
        return super.toString() + ", dep[" + bean2 + "]";
    }
}
