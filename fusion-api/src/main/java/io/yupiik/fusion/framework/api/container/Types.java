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
package io.yupiik.fusion.framework.api.container;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public class Types {
    public boolean isAssignable(final Type subClassOrEquals, final Type api) {
        return isAssignable(subClassOrEquals, api, new HashSet<>());
    }

    private boolean isAssignable(final Type subClassOrEquals, final Type api, final Set<Class<?>> visited) {
        if (subClassOrEquals instanceof Class<?> sub && api instanceof Class<?> base) {
            return base.isAssignableFrom(sub);
        }

        // exact matching for complex types
        if (Objects.equals(api, subClassOrEquals)) {
            return true;
        }

        if (api instanceof Class<?> clazz &&
                subClassOrEquals instanceof ParameterizedType pt &&
                pt.getRawType() instanceof Class<?> raw) {
            return isAssignable(raw, clazz, visited);
        }

        if (api instanceof ParameterizedType && subClassOrEquals instanceof Class<?> c && visited.add(c)) {
            return Stream.of(c.getGenericInterfaces()).anyMatch(it -> isAssignable(it, api)) ||
                    (c != Object.class && c.getGenericSuperclass() != null && isAssignable(c.getGenericSuperclass(), api, visited));
        }

        return false;
    }

    public static class ParameterizedTypeImpl implements ParameterizedType {
        private final Type raw;
        private final Type[] args;
        private final int hash;

        public ParameterizedTypeImpl(final Type raw, final Type... args) {
            this.raw = raw;
            this.args = args;

            this.hash = Arrays.hashCode(args) ^ Objects.hashCode(raw);
        }

        @Override
        public Type[] getActualTypeArguments() {
            return args;
        }

        @Override
        public Type getRawType() {
            return raw;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }

            if (!(o instanceof ParameterizedType pt)) {
                return false;
            }

            final var thatRawType = pt.getRawType();
            return Objects.equals(raw, thatRawType) && Arrays.equals(args, pt.getActualTypeArguments());
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            final var sb = new StringBuilder();
            sb.append(raw.getTypeName());
            if (args != null) {
                sb.append(Stream.of(args).map(Type::getTypeName).collect(joining(", ", "<", ">")));
            }
            return sb.toString();
        }
    }
}
