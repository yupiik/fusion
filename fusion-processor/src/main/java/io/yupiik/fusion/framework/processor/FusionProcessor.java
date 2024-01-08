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
package io.yupiik.fusion.framework.processor;

import javax.annotation.processing.Completion;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Set;

// hides the actual processor and classes to avoid to get completion in IDE for them, we just expose this class
public class FusionProcessor implements Processor {
    private final Processor delegate = createDelegate();

    private Processor createDelegate() {
        final var currentThread = Thread.currentThread();
        final var tccl = currentThread.getContextClassLoader();
        final var loader = new InternalClassLoader(FusionProcessor.class.getClassLoader());
        try {
            currentThread.setContextClassLoader(loader);
            final var internalProcessor = loader.loadClass("io.yupiik.fusion.framework.processor.internal.InternalFusionProcessor");
            final var lookup = MethodHandles.privateLookupIn(FusionProcessor.class, MethodHandles.lookup());
            final var constructor = lookup.findConstructor(internalProcessor, MethodType.methodType(void.class));
            final var instance = constructor.invoke();
            return (Processor) Proxy.newProxyInstance(loader, new Class<?>[]{Processor.class}, (proxy, method, args) -> {
                final var thread = Thread.currentThread();
                final var old = thread.getContextClassLoader();
                thread.setContextClassLoader(loader);
                try {
                    return method.invoke(instance, args);
                } catch (final InvocationTargetException ite) {
                    throw ite.getTargetException();
                } finally {
                    thread.setContextClassLoader(old);
                }
            });
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable e) {
            throw new IllegalStateException(e);
        } finally {
            currentThread.setContextClassLoader(tccl);
        }
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        return delegate.process(annotations, roundEnv);
    }

    @Override
    public Set<String> getSupportedOptions() {
        return delegate.getSupportedOptions();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return delegate.getSupportedAnnotationTypes();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return delegate.getSupportedSourceVersion();
    }

    @Override
    public void init(final ProcessingEnvironment processingEnv) {
        delegate.init(processingEnv);
    }

    @Override
    public Iterable<? extends Completion> getCompletions(final Element element, final AnnotationMirror annotation, final ExecutableElement member, final String userText) {
        return delegate.getCompletions(element, annotation, member, userText);
    }

    // goal there is to hide the processor classes from the completion of the IDE so we move:
    // X.class -> fusion/annotationprocessor/precompiled/X.classF
    private static class InternalClassLoader extends ClassLoader {
        static {
            registerAsParallelCapable();
        }

        private InternalClassLoader(final ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                final var loaded = super.findLoadedClass(name);
                if (loaded != null) {
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }

                if (name.startsWith("io.yupiik.fusion.framework.processor.internal.")) {
                    // classF enables to not have completion on these classes in IDE
                    final var resource = getParent().getResourceAsStream(toResource(name));
                    if (resource != null) {
                        final byte[] bytes;
                        try (resource) {
                            bytes = resource.readAllBytes();
                        } catch (final IOException e) {
                            throw new IllegalStateException(e);
                        }

                        final var clazz = super.defineClass(name, bytes, 0, bytes.length);
                        if (resolve) {
                            resolveClass(clazz);
                        }
                        return clazz;
                    }
                }

                return super.loadClass(name, resolve);
            }
        }

        private String toResource(final String name) {
            return "fusion/annotationprocessor/precompiled/" + name.replace('.', '/') + ".classF";
        }
    }
}
