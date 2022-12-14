package test.p;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.event.OnEvent;
import io.yupiik.fusion.framework.build.api.order.Order;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class Listening {
    private final List<String> events = new ArrayList<>();

    protected void onEvent(@OnEvent @Order(2) final String event) {
        events.add(event);
    }

    @Override
    public String toString() {
        return String.join(", ", events);
    }
}
