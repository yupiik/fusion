package test.p;

import io.yupiik.fusion.framework.api.event.Emitter;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.scanning.Injection;

@ApplicationScoped
public class Emitting {
    @Injection
    Emitter emit;

    @Override
    public String toString() {
        final var str = '>' + getClass().getSimpleName() + "<";
        emit.emit(str);
        return str;
    }
}
