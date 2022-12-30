package io.yupiik.fusion.testing.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FusionCLITestTest {
    @FusionCLITest(args = {"test", "run"})
    void run(final Stdout stdout) {
        assertEquals("Args=Args[args=[test, run]]", stdout.content().strip());
    }
}
