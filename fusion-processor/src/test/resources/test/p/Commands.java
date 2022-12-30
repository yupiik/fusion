package test.p;


import io.yupiik.fusion.framework.api.event.Emitter;
import io.yupiik.fusion.framework.build.api.cli.Command;
import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;

import java.util.List;

public interface Commands {
    @Command(name = "c1", description = "A super command.")
    public static class C1 implements Runnable {
        private final Conf conf;
        private final Emitter aBean;

        public C1(final Conf conf, final Emitter aBean) {
            this.conf = conf;
            this.aBean = aBean;
        }

        @Override
        public void run() {
            System.setProperty(C1.class.getName(), "conf=" + conf + ", bean = " + (aBean != null));
        }

        @RootConfiguration("c1")
        public record Conf(@Property(documentation = "The main name.") String name, Nested nested,
                           List<Nested> nesteds,
                           List<String> list) {}

        public record Nested(String lower) {}
    }
}