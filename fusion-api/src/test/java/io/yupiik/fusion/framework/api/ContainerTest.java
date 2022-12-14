package io.yupiik.fusion.framework.api;

import io.yupiik.fusion.framework.api.container.Generation;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.api.lifecycle.Start;
import io.yupiik.fusion.framework.api.lifecycle.Stop;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.build.api.scanning.Injection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerTest {
    @Test
    void simple() {
        try (final var container = ConfiguringContainer.of()
                .register(new Bean1$FusionBean(), new Bean2$FusionBean())
                .start();
             final var lookup = container.lookup(Bean1.class)) {
            assertEquals("bean1[bean2[]]", lookup.instance().toString());
        }
    }

    @Test
    void inheritance() {
        try (final var container = ConfiguringContainer.of()
                .register(new Bean1$FusionBean(), new Bean2$FusionBean())
                .start();
             final var lookup = container.lookup(Supplier.class)) {
            assertEquals("bean1[bean2[]]", lookup.instance().toString());
        }
    }

    @Test
    void containerLifecycle() {
        final var startListener = new ArrayList<Start>();
        final var stopListener = new ArrayList<Stop>();
        try (final var ignored = ConfiguringContainer.of().register(
                        new FusionListener<Start>() {
                            @Override
                            public Class<Start> eventType() {
                                return Start.class;
                            }

                            @Override
                            public void onEvent(final RuntimeContainer container, final Start event) {
                                startListener.add(event);
                            }
                        },
                        new FusionListener<Stop>() {
                            @Override
                            public Class<Stop> eventType() {
                                return Stop.class;
                            }

                            @Override
                            public void onEvent(final RuntimeContainer container, final Stop event) {
                                stopListener.add(event);
                            }
                        })
                .start()) {
            assertEquals(1, startListener.size());
            assertTrue(stopListener.isEmpty());
        }
        assertEquals(1, startListener.size());
        assertEquals(1, stopListener.size());
    }

    @Generation(version = 1)
    public static class Bean1 implements Supplier<String> {
        @Injection
        private Bean2 bean2; // note: private cause compilation as a single compilation unit

        @Override
        public String toString() {
            return "bean1[" + bean2 + "]";
        }

        @Override
        public String get() {
            return toString();
        }
    }

    @Generation(version = 1)
    public static class Bean1$FusionBean implements FusionBean<Bean1> {
        @Override
        public Type type() {
            return Bean1.class;
        }

        @Override
        public Class<?> scope() {
            return DefaultScoped.class;
        }

        @Override
        public Bean1 create(final RuntimeContainer container, final List<Instance<?>> dependents) {
            final var instance = new Bean1();
            {
                final var instance__bean2 = container.lookup(Bean2.class);
                instance.bean2 = instance__bean2.instance();
                dependents.add(instance__bean2);
            }
            return instance;
        }
    }

    public static class Bean2 {
        @Override
        public String toString() {
            return "bean2[]";
        }
    }

    public static class Bean2$FusionBean implements FusionBean<Bean2> {
        @Override
        public Type type() {
            return Bean2.class;
        }

        @Override
        public Class<?> scope() {
            return DefaultScoped.class;
        }

        @Override
        public Bean2 create(final RuntimeContainer container, final List<Instance<?>> dependents) {
            return new Bean2();
        }
    }
}
