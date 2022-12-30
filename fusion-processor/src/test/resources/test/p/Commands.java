package test.p;


import io.yupiik.fusion.framework.api.event.Emitter;
import io.yupiik.fusion.framework.build.api.cli.Command;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;

public interface Commands {
    @Command(name = "c1", description = "A super command.")
    public class C1 implements Runnable {
        private final Conf conf;
        private final Emitter aBean;

        public C1(final Conf conf, final Emitter aBean) {
            this.conf = conf;
            this.aBean = aBean;
        }

        @Override
        public void run() {
            System.setProperty(C1.class.getName(), conf.name() + ", bean = " + (aBean != null));
        }

        @RootConfiguration("c1")
        public record Conf(String name) {}
    }
}