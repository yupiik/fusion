package test.p;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.lifecycle.Destroy;
import io.yupiik.fusion.framework.build.api.lifecycle.Init;
import io.yupiik.fusion.framework.build.api.scanning.Bean;
import io.yupiik.fusion.framework.build.api.scanning.Injection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public interface NestedBeans {
    @ApplicationScoped
    public static class Lifecycled extends LifecycledDep {
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

    @ApplicationScoped
    public static class MethodProducer {
        @Injection
        Bean2 bean2;

        private static final List<String> EVENTS = new ArrayList<>();

        @Bean
        public Produceable create() {
            EVENTS.add("create");
            return new Produceable() {
                @Override
                public void close() throws Exception {
                    EVENTS.add("create.close");
                }

                @Override
                public String get() {
                    EVENTS.add("create.get(" + bean2 + ")");
                    return bean2.toString();
                }
            };
        }

        @Bean
        public static String globalConf() {
            EVENTS.add("globalConf");
            return "<conf>";
        }

        @Override
        public String toString() {
            return String.join(", ", EVENTS);
        }

        public interface Produceable extends Supplier<String>, AutoCloseable {
        }
    }
}