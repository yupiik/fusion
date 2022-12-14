package io.yupiik.fusion.framework.api.container;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
class TypesTest {
    private final Types types = new Types();

    @Test
    void classes() {
        assertTrue(types.isAssignable(Impl.class, Impl.class));
        assertTrue(types.isAssignable(Api.class, Api.class));
        assertTrue(types.isAssignable(Impl.class, Api.class));
        assertFalse(types.isAssignable(Api.class, Impl.class));
    }

    @Test
    void parameterizedType() {
        assertTrue(types.isAssignable(
                new Types.ParameterizedTypeImpl(List.class, Api.class),
                new Types.ParameterizedTypeImpl(List.class, Api.class)));
    }

    public interface Api {
    }

    public static class Impl implements Api {
    }
}
