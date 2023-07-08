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
package io.yupiik.fusion.framework.processor.internal;

import io.yupiik.fusion.framework.api.container.Types;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.List;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.ElementKind.ENUM_CONSTANT;

public record ParsedType(Type type, String className, String raw, List<String> args, List<String> enumValues) {
    public static ParsedType of(final TypeMirror type) {
        if (type instanceof DeclaredType dt && !dt.getTypeArguments().isEmpty()) {
            final var element = dt.asElement();
            return new ParsedType(
                    Type.PARAMETERIZED_TYPE, null,
                    element instanceof TypeElement te ? te.getQualifiedName().toString() : element.toString(),
                    dt.getTypeArguments().stream()
                            .map(TypeMirror::toString) // simplistic for now
                            .toList(),
                    element.getKind() == ElementKind.ENUM ? findEnumValues(element) : null);
        }
        return new ParsedType(Type.CLASS, type.toString(), null, null,
                type instanceof DeclaredType dt ? ofNullable(dt.asElement())
                        .filter(it -> it.getKind() == ElementKind.ENUM)
                        .map(ParsedType::findEnumValues)
                        .orElse(null) : null);
    }

    public String simpleName(final String value) {
        return value.substring(value.lastIndexOf('.') + 1);
    }

    public String createParameterizedTypeImpl() {
        if (type != Type.PARAMETERIZED_TYPE) {
            throw new IllegalStateException("only PARAMETERIZED_TYPE can call createParameterizedTypeImpl()");
        }
        return "new " + Types.ParameterizedTypeImpl.class.getName().replace('$', '.') + "(" +
                raw() + ".class, " +
                args().stream().map(a -> a + ".class").collect(joining(",")) + ")";
    }

    public String createParameterizedTypeCast() {
        if (type != Type.PARAMETERIZED_TYPE) {
            throw new IllegalStateException("only PARAMETERIZED_TYPE can call createParameterizedTypeCast()");
        }
        return "(" + raw() + '<' + String.join(", ", args()) + ">) ";
    }

    private static List<String> findEnumValues(final Element element) {
        return element.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ENUM_CONSTANT)
                .map(it -> it.getSimpleName().toString())
                .toList();
    }

    public enum Type {
        CLASS, PARAMETERIZED_TYPE
    }
}