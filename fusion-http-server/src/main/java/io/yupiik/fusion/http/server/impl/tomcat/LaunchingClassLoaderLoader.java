/*
 * Copyright (c) 2022-2023 - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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
