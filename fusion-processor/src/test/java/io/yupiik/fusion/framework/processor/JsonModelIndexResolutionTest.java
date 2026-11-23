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
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonModelIndexResolutionTest {
    @Test
    void reusesResolvedNamesButAllowsTypesToAppearInLaterRounds(@TempDir final Path work) throws Exception {
        final var types = new HashMap<String, TypeElement>();
        // A literal dollar in a top-level name must win over a possible nesting interpretation.
        types.put("p.Literal$Name", type("Literal$Name", "p.Literal$Name", List.of()));
        types.put("p.Literal", type("Literal", "p.Literal", List.of(type("Name", "p.Literal.Name", List.of()))));
        final var lookups = new AtomicInteger();
        final var literalLookups = new AtomicInteger();
        final var elements = (Elements) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Elements.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getTypeElement")) {
                        lookups.incrementAndGet();
                        if (arguments[0].toString().equals("p.Literal$Name")) {
                            literalLookups.incrementAndGet();
                        }
                        return types.get(arguments[0].toString());
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        final var environment = (ProcessingEnvironment) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ProcessingEnvironment.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getElementUtils")) {
                        return elements;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        final var indexType = new ProcessorInternals(getClass().getClassLoader())
                .loadClass("io.yupiik.fusion.framework.processor.internal.JsonModelIndex");
        Files.writeString(work.resolve("json-models-v1.properties"), "p.Outer$Inner=\np.Literal$Name=\n");
        final var index = indexType.getConstructor(ProcessingEnvironment.class, Path.class, Path.class)
                .newInstance(environment, work, null);
        final var discover = indexType.getMethod("discover");
        assertEquals(Set.of("p.Outer$Inner", "p.Literal$Name"), ProcessorInternals.invoke(discover, index));
        final var nested = type("Inner", "p.Outer.Inner", List.of());
        types.put("p.Outer", type("Outer", "p.Outer", List.of(nested)));
        final var names = Set.of("p.Outer$Inner", "p.Outer.Inner", "p.Literal$Name");
        assertEquals(names, ProcessorInternals.invoke(discover, index));
        final var resolvedLookups = lookups.get();
        for (int i = 0; i < 10; i++) {
            assertEquals(names, ProcessorInternals.invoke(discover, index));
        }
        assertEquals(resolvedLookups, lookups.get());
        assertEquals(1, literalLookups.get());
    }

    private TypeElement type(final String simple, final String qualified, final List<TypeElement> members) {
        return (TypeElement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{TypeElement.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getSimpleName" -> name(simple);
                    case "getQualifiedName" -> name(qualified);
                    case "getEnclosedElements" -> members;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private Name name(final String value) {
        return new Name() {
            @Override
            public boolean contentEquals(final CharSequence other) {
                return value.contentEquals(other);
            }

            @Override
            public int length() {
                return value.length();
            }

            @Override
            public char charAt(final int index) {
                return value.charAt(index);
            }

            @Override
            public CharSequence subSequence(final int start, final int end) {
                return value.subSequence(start, end);
            }

            @Override
            public String toString() {
                return value;
            }
        };
    }
}
