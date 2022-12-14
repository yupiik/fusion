package io.yupiik.fusion.testing;

import io.yupiik.fusion.framework.api.event.Emitter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@FusionSupport
class FusionSupportTest {
    @Test
    void run(@Fusion final Emitter emitter) {
        assertNotNull(emitter);
    }
}
