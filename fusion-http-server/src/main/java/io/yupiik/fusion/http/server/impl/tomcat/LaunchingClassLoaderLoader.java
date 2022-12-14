package io.yupiik.fusion.http.server.impl.tomcat;

import org.apache.catalina.Context;
import org.apache.catalina.Loader;

import java.beans.PropertyChangeListener;

public class LaunchingClassLoaderLoader implements Loader {
    private final ClassLoader loader = Thread.currentThread().getContextClassLoader();
    private Context context;

    @Override
    public ClassLoader getClassLoader() {
        return loader;
    }

    @Override
    public Context getContext() {
        return context;
    }

    @Override
    public void setContext(final Context context) {
        this.context = context;
    }

    @Override
    public void backgroundProcess() {
        // no-op
    }

    @Override
    public boolean getDelegate() {
        return false;
    }

    @Override
    public void setDelegate(final boolean delegate) {
        // no-op
    }

    @Override
    public void addPropertyChangeListener(final PropertyChangeListener listener) {
        // no-op
    }

    @Override
    public boolean modified() {
        return false;
    }

    @Override
    public void removePropertyChangeListener(final PropertyChangeListener listener) {
        // no-op
    }
}
