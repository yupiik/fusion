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

import io.yupiik.fusion.framework.processor.test.ProcessorInternals;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileManager;
import javax.tools.SimpleJavaFileObject;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompilerOutputCleanupTest {
    @Test
    void delegatesNonFileSourceAndClassDeletionToTheirFilerObjects() throws Exception {
        final var deleted = new ArrayList<String>();
        final var filer = (Filer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Filer.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "createSourceFile" -> output("round/" + arguments[0] + ".java", deleted);
                    case "getResource" -> output(((JavaFileManager.Location) arguments[0]).getName() + '/' +
                            arguments[1] + '/' + arguments[2], deleted);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        final var environment = (ProcessingEnvironment) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ProcessingEnvironment.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getFiler" -> filer;
                    case "getOptions" -> Map.of();
                    case "getElementUtils" -> Proxy.newProxyInstance(getClass().getClassLoader(),
                            new Class<?>[]{Elements.class},
                            (elementProxy, elementMethod, elementArguments) -> {
                                throw new UnsupportedOperationException(elementMethod.getName());
                            });
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        final var emitterType = new ProcessorInternals(getClass().getClassLoader())
                .loadClass("io.yupiik.fusion.framework.processor.internal.generator.BindingEmitter");
        final var emitter = emitterType.getConstructor(ProcessingEnvironment.class, Path.class, Path.class)
                .newInstance(environment, null, null);
        ProcessorInternals.invoke(emitterType.getMethod("defer"), emitter);
        ProcessorInternals.invoke(emitterType.getMethod("deleteRoundSources"), emitter);
        assertEquals(1, deleted.size());
        ProcessorInternals.invoke(emitterType.getMethod("deleteGeneratedClass", ProcessingEnvironment.class, Path.class, String.class),
                null, environment, null, "example.FusionBindings_test");
        assertEquals(List.of("CLASS_OUTPUT/example/FusionBindings_test.class", "SOURCE_OUTPUT/example/FusionBindings_test.java"),
                deleted.subList(1, deleted.size()));
        ProcessorInternals.invoke(emitterType.getMethod("deleteRoundSources"), emitter);
        assertEquals(3, deleted.size(), "Consumed round outputs are deleted only once");
    }

    private SimpleJavaFileObject output(final String name, final List<String> deleted) {
        return new SimpleJavaFileObject(URI.create("memory:///" + name), javax.tools.JavaFileObject.Kind.SOURCE) {
            @Override
            public Writer openWriter() {
                return new StringWriter();
            }

            @Override
            public boolean delete() {
                deleted.add(name);
                return true;
            }
        };
    }
}
