package test.p;

import io.yupiik.fusion.framework.build.api.order.Order;

@Order(2)
public class OrderedBean2 extends OrderedBean1 {
    @Override
    public String toString() {
        return "bean2";
    }
}
