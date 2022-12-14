package test.p;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;

import java.util.List;

@ApplicationScoped // ensure a normal scoped instance works - with subclassing challenge, which means default works too
public class ConstructorInjection {
    private final String value;

    protected ConstructorInjection() { // for proxies/context
        this.value = null;
    }

    public ConstructorInjection(final Bean2 bean2, final List<Bean21> list) {
        this.value = "constructor<bean2=" + bean2 + ",list=" + list + ">";
    }

    @Override
    public String toString() {
        return value;
    }
}
