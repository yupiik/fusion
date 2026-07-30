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
package io.yupiik.fusion.framework.processor.internal.emit;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

import static java.util.stream.Collectors.joining;

// converts javax.lang.model types to JVM binary names/descriptors for the bytecode emitter
public final class Descriptors {
    private Descriptors() {
        // no-op
    }

    public static String binaryName(final ProcessingEnvironment environment, final TypeMirror type) {
        final var erased = environment.getTypeUtils().erasure(type);
        final var element = environment.getTypeUtils().asElement(erased);
        if (element instanceof TypeElement te) {
            return environment.getElementUtils().getBinaryName(te).toString();
        }
        return erased.toString(); // primitives/arrays are not used through this path
    }

    public static String descriptorOf(final ProcessingEnvironment environment, final TypeMirror type) {
        final var erased = environment.getTypeUtils().erasure(type);
        return switch (erased.getKind()) {
            case BOOLEAN -> "Z";
            case BYTE -> "B";
            case SHORT -> "S";
            case INT -> "I";
            case LONG -> "J";
            case CHAR -> "C";
            case FLOAT -> "F";
            case DOUBLE -> "D";
            case VOID -> "V";
            case ARRAY -> "[" + descriptorOf(environment, ((ArrayType) erased).getComponentType());
            default -> "L" + binaryName(environment, erased).replace('.', '/') + ";";
        };
    }

    public static String descriptorOf(final ProcessingEnvironment environment, final ExecutableType method) {
        return method.getParameterTypes().stream()
                .map(it -> descriptorOf(environment, it))
                .collect(joining("", "(", ")")) +
                descriptorOf(environment, method.getReturnType());
    }

    public static String descriptorOf(final ProcessingEnvironment environment, final ExecutableElement method) {
        return descriptorOf(environment, (ExecutableType) method.asType());
    }
}
