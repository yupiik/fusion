package test.p;

import io.yupiik.fusion.framework.build.api.scanning.Injection;

import java.util.List;

public class InjectedListAutoSorted {
    @Injection
    List<OrderedBean1> beans;

    @Override
    public String toString() {
        return "bean1{" + beans + "}";
    }
}
