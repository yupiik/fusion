package test.p;

import io.yupiik.fusion.framework.build.api.order.Order;

@Order(1)
public class OrderedBean1 {
    @Override
    public String toString() {
        return "bean1";
    }
}
