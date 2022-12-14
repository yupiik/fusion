package io.yupiik.fusion.framework.processor.test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class CompilationClassLoader extends URLClassLoader implements AutoCloseable {
    private final Thread thread;
    private final ClassLoader oldLoader;

    public CompilationClassLoader(final Path output) throws MalformedURLException {
        super(new URL[]{output.toUri().toURL()}, getSystemClassLoader());

        thread = Thread.currentThread();
        oldLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(this);
    }

    @Override
    public void close() {
        try {
            super.close();
        } catch (final IOException e) {
            // no-op
        }
        thread.setContextClassLoader(oldLoader);
    }
}
