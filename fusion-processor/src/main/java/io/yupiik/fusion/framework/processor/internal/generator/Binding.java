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
package io.yupiik.fusion.framework.processor.internal.generator;

import java.util.List;

/**
 * A source fragment, not a generated class. The result's name is its stable identity and ordering key.
 * Direct factory expressions contain typed callbacks and $callback{operation} helper references.
 * Direct helper calls use $helper{operation}; method declarations use $method.
 * These exact tokens, $factory{stableId}, and the $method_ helper prefix are reserved outside Java literals/comments.
 * Other dollar-containing Java identifiers are business identifiers and are left unchanged.
 * Registration type is a source type (for example FusionBean<?>), not the business type.
 * A null kind means standalone factory only; bean/listener kinds are registered in the module.
 * Former generated names determine manual typed construction method names on the actual construction owner.
 * Expressions and private helpers belong to their consuming module or bounded package construction owner.
 * There is no standalone facade or runtime identity registry; owner names and helper ordinals are not contracts.
 */
public record Binding(String packageName, String kind, String registrationType,
                      String factoryExpression, List<Method> methods) {
    public Binding {
        methods = List.copyOf(methods);
    }

    /** A typed static method reference resolved in the artifact's holder namespace. */
    public static String helperReference(final int operation) {
        return "$callback{" + operation + "}";
    }

    /** A direct call to another helper in the same artifact. */
    public static String helperCall(final int operation) {
        return "$helper{" + operation + "}";
    }

    /** Source weight used to bound holder compilation units without materializing record debug strings. */
    public int sourceSize() {
        int size = packageName.length() + registrationType.length() + factoryExpression.length();
        for (final var method : methods) {
            size += method.declaration().length() + method.inlineExpression().length();
        }
        return size;
    }

    /** Symbolic factory call, resolved after all families have contributed their fragments. */
    public static String reference(final String stableId) {
        return "$factory{" + stableId + "}";
    }

    /** Multiple artifacts can share one originating generator result (for example codec and codec bean). */
    public record Artifact(String stableId, Binding binding) {
    }

    /**
     * A typed helper declaration and its artifact-local identity, not a runtime operation.
     * An optional explicit callback expression avoids a helper for trivial generated bodies.
     * Expressions are supplied by generators, never inferred by parsing Java declarations.
     */
    public record Method(int operation, String declaration, String inlineExpression) {
        public Method(final int operation, final String declaration) {
            this(operation, declaration, "");
        }

        /** Supplies a self-contained lambda or method reference for a trivial callback. */
        public static Method callback(final int operation, final String declaration, final String inlineExpression) {
            return new Method(operation, declaration, inlineExpression);
        }
    }
}
