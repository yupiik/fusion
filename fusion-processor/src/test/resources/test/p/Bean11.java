package test.p;

import io.yupiik.fusion.framework.build.api.scanning.Injection;

import java.util.List;

public class Bean11 {
    @Injection
    List<Bean21> bean2;

    @Override
    public String toString() {
        return "bean1{" + bean2 + "}";
    }
}
