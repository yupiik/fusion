package io.yupiik.fusion.http.server;

import jakarta.servlet.http.HttpServlet;
import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServerConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebServerTest {
    @Test
    void runEmpty() {
        try (final var server = WebServer.of(WebServer.Configuration.of().port(0))) {
            assertNotEquals(0, server.configuration().port());
        }
    }

    @Test
    void stopOnFailure() {
        final var configuration = WebServer.Configuration.of().port(0);
        configuration.unwrap(TomcatWebServerConfiguration.class).setInitializers(List.of((set, servletContext) -> {
            final var servlet = servletContext.addServlet("test", new HttpServlet() {
                @Override
                public void init() {
                    throw new IllegalArgumentException();
                }
            });
            servlet.setLoadOnStartup(1);
            servlet.addMapping("/test");
        }));
        final var threadsBefore = listThreads();
        assertThrows(IllegalStateException.class, () -> WebServer.of(configuration));

        final var maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) { // support a small retry for the CI
            try {
                assertEquals(Set.of(threadsBefore), Set.of(listThreads()));
                break;
            } catch (final AssertionError ae) {
                if (i + 1 == maxRetries) {
                    throw ae;
                }
                try {
                    sleep(100);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    private Thread[] listThreads() {
        final var threads = new Thread[Thread.activeCount()];
        Thread.enumerate(threads);
        return threads;
    }
}
