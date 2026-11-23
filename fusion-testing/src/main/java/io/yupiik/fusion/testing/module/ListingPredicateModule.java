/*
 * Copyright (c) 2022 - present - Yupiik SAS - https://www.yupiik.com
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
package io.yupiik.fusion.testing.module;

import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.lifecycle.Start;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Constructor-based fallback discovery for legacy or manually written descriptors.
 * Ordinary annotation-processor registrations are provided by automatically discovered {@link FusionModule}s,
 * independently of this module's filename predicates.
 */
public class ListingPredicateModule implements FusionModule {
    private final List<FusionBean<?>> beans = new CopyOnWriteArrayList<>();
    private final List<FusionListener<?>> listeners = new CopyOnWriteArrayList<>();

    /**
     * @param beanPredicate filter on legacy bean descriptor class filenames, not automatic module registrations.
     * @param listenerPredicate filter on legacy listener descriptor class filenames, not automatic module registrations.
     * @param dirs directories to scan; dependencies outside these directories are not scanned.
     */
    public ListingPredicateModule(final Predicate<String> beanPredicate,
                                  final Predicate<String> listenerPredicate,
                                  final List<Path> dirs) {
        find(beanPredicate, listenerPredicate, dirs);
    }

    @Override
    public int priority() { // "last" - filter other modules before exposing our descriptors
        return Integer.MAX_VALUE - 100;
    }

    @Override
    public Stream<FusionBean<?>> beans() {
        return beans.stream();
    }

    @Override
    public Stream<FusionListener<?>> listeners() {
        if (listeners.isEmpty() && beans.isEmpty()) {
            return Stream.empty();
        }
        return Stream.concat(listeners.stream(), Stream.of(new FusionListener<Start>() {
            @Override
            public int priority() {
                return Integer.MAX_VALUE;
            }

            @Override
            public Type eventType() {
                return Start.class;
            }

            @Override
            public void onEvent(final RuntimeContainer container, final Start event) {
                beans.clear();
                listeners.clear();
            }
        }));
    }

    @Override
    public BiPredicate<RuntimeContainer, FusionBean<?>> beanFilter() {
        return (container, bean) -> {
            if (beans.stream().noneMatch(discovered -> discovered == bean)) {
                // A normal module owns its registrations; remove only our legacy fallback of the same class.
                beans.removeIf(discovered -> discovered.getClass() == bean.getClass());
            }
            return true;
        };
    }

    @Override
    public BiPredicate<RuntimeContainer, FusionListener<?>> listenerFilter() {
        return (container, listener) -> {
            if (listeners.stream().noneMatch(discovered -> discovered == listener)) {
                listeners.removeIf(discovered -> discovered.getClass() == listener.getClass());
            }
            return true;
        };
    }

    private void find(final Predicate<String> beanPredicate, final Predicate<String> listenerPredicate, final List<Path> dirs) {
        final var loader = Thread.currentThread().getContextClassLoader();
        final var visited = new HashSet<String>();
        dirs.forEach(dir -> {
            try {
                Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                        final var filename = file.getFileName().toString();
                        if (!filename.endsWith(".class")) {
                            return super.visitFile(file, attrs);
                        }
                        final var relative = dir.relativize(file).normalize().toString();
                        if (relative.startsWith("META-INF")) {
                            return super.visitFile(file, attrs);
                        }
                        final var className = toClassName(relative);
                        if (!visited.add(className)) {
                            return super.visitFile(file, attrs);
                        }
                        try {
                            if (beanPredicate.test(filename)) {
                                final var clazz = load(loader, className);
                                if (clazz != null && FusionBean.class.isAssignableFrom(clazz)) {
                                    beans.add(clazz.asSubclass(FusionBean.class).getConstructor().newInstance());
                                }
                            }
                            if (listenerPredicate.test(filename)) {
                                final var clazz = load(loader, className);
                                if (clazz != null && FusionListener.class.isAssignableFrom(clazz)) {
                                    listeners.add(clazz.asSubclass(FusionListener.class).getConstructor().newInstance());
                                }
                            }
                        } catch (final ReflectiveOperationException e) {
                            throw failure(e);
                        }
                        return super.visitFile(file, attrs);
                    }
                });
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private IllegalStateException failure(final ReflectiveOperationException error) {
        return new IllegalStateException(error instanceof InvocationTargetException invocation ? invocation.getTargetException() : error);
    }

    private Class<?> load(final ClassLoader loader, final String name) {
        try {
            final var clazz = loader.loadClass(name);
            clazz.getConstructor(); // legacy/manual descriptors retain constructor-based discovery
            return clazz;
        } catch (final ClassNotFoundException | NoSuchMethodException e) {
            return null;
        }
    }

    private String toClassName(final String resource) {
        return resource.substring(0, resource.length() - ".class".length()).replace(File.separatorChar, '.');
    }
}
