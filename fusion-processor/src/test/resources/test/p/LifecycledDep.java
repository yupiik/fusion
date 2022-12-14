package test.p;

import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.build.api.lifecycle.Destroy;
import io.yupiik.fusion.framework.build.api.lifecycle.Init;

@DefaultScoped
public class LifecycledDep {
    private int init = 0;
    private int destroyed = 0;

    @Init
    protected void init() {
        init++;
    }

    @Destroy
    protected void destroy() {
        destroyed++;
    }

    @Override
    public String toString() {
        return "init=" + init + ", destroyed=" + destroyed;
    }
}
