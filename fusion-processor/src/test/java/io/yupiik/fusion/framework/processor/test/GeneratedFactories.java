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
package io.yupiik.fusion.framework.processor.test;

import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.api.container.FusionModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;

/** Test-only discovery of public typed construction methods on fixture output owners, never a runtime registry. */
public final class GeneratedFactories {
    private GeneratedFactories() {
        // no-op
    }

    public static List<FusionBean<?>> beans(final ClassLoader loader) {
        return registrations(loader, "beans").<FusionBean<?>>map(it -> (FusionBean<?>) it).toList();
    }

    public static List<FusionListener<?>> listeners(final ClassLoader loader) {
        return registrations(loader, "listeners").<FusionListener<?>>map(it -> (FusionListener<?>) it).toList();
    }

    public static FusionBean<?> bean(final ClassLoader loader, final Type type) {
        final var matches = beans(loader).stream().filter(it -> it.type().equals(type)).toList();
        if (matches.size() != 1) {
            throw new IllegalStateException("Expected one registered bean for " + type + ", got " + matches.size());
        }
        return matches.get(0);
    }

    // Resolve the API in the fixture loader too: some packaging tests deliberately isolate the runtime API.
    public static Stream<?> registrations(final ClassLoader loader, final String kind) {
        try {
            final var moduleType = loader.loadClass(FusionModule.class.getName());
            final var method = moduleType.getMethod(kind);
            return ServiceLoader.load(moduleType, loader).stream()
                    .filter(it -> it.type().getClassLoader() == loader)
                    .flatMap(it -> {
                        try {
                            try (final var registrations = (Stream<?>) method.invoke(it.get())) {
                                return registrations.toList().stream();
                            }
                        } catch (final ReflectiveOperationException e) {
                            throw new IllegalStateException(e);
                        }
                    });
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> T invoke(final ClassLoader loader, final String packageName, final String method, final Class<T> expected) {
        final var matches = methods(loader, packageName, expected).stream().filter(it -> it.getName().equals(method)).toList();
        if (matches.size() != 1) {
            throw new IllegalStateException("Expected one construction method for " + packageName + "." + method + ", got " + matches);
        }
        try {
            return expected.cast(matches.get(0).invoke(null));
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot invoke " + matches.get(0), e);
        }
    }

    public static List<Method> methods(final ClassLoader loader, final String packageName, final Class<?> expected) {
        if (!(loader instanceof URLClassLoader fixtureLoader)) {
            throw new IllegalArgumentException("Expected a fixture URLClassLoader: " + loader);
        }
        final var prefix = packageName.isEmpty() ? "" : packageName.replace('.', '/') + "/";
        final Class<?> moduleType;
        try {
            moduleType = loader.loadClass(FusionModule.class.getName());
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        return Arrays.stream(fixtureLoader.getURLs()).flatMap(url -> {
            try {
                final var root = Path.of(url.toURI());
                final List<String> names;
                if (Files.isDirectory(root)) {
                    final var directory = root.resolve(prefix);
                    if (!Files.isDirectory(directory)) {
                        return Stream.<String>empty();
                    }
                    try (final var files = Files.list(directory)) {
                        names = files.filter(it -> it.toString().endsWith(".class"))
                                .map(it -> prefix + it.getFileName()).toList();
                    }
                } else {
                    try (final var jar = new JarFile(root.toFile())) {
                        names = jar.stream().map(it -> it.getName())
                                .filter(it -> it.startsWith(prefix) && it.endsWith(".class"))
                                .filter(it -> !it.substring(prefix.length()).contains("/"))
                                .toList();
                    }
                }
                return names.stream();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            } catch (final java.net.URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        }).distinct().flatMap(name -> declaredMethods(loader, name))
                .filter(it -> Modifier.isPublic(it.getDeclaringClass().getModifiers()))
                .filter(it -> Modifier.isPublic(it.getModifiers()) && Modifier.isStatic(it.getModifiers()))
                .filter(it -> it.getParameterCount() == 0 && expected.isAssignableFrom(it.getReturnType()))
                .collect(groupingBy(Method::getName)).values().stream()
                .flatMap(methods -> {
                    // A root module can expose an overflow entry point too; inspect the real owner once.
                    final var owners = methods.stream().filter(it -> !moduleType.isAssignableFrom(it.getDeclaringClass())).toList();
                    return owners.isEmpty() ? methods.stream() : owners.stream();
                })
                .sorted(Comparator.comparing(Method::getName).thenComparing(it -> it.getDeclaringClass().getName()))
                .toList();
    }

    private static Stream<Method> declaredMethods(final ClassLoader loader, final String name) {
        try {
            return Arrays.stream(loader.loadClass(name.substring(0, name.length() - ".class".length()).replace('/', '.')).getDeclaredMethods());
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
