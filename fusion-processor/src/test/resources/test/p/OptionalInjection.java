package test.p;

import io.yupiik.fusion.framework.build.api.scanning.Injection;

import java.util.Optional;

public class OptionalInjection {
    @Injection
    Optional<NotABean> bean2;

    @Override
    public String toString() {
        return "bean1<" + bean2 + ">";
    }
}