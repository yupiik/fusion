package io.yupiik.fusion.framework.api.container;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public class Types {
    public boolean isAssignable(final Type subClassOrEquals, final Type api) {
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
            return isAssignable(raw, clazz);
        }

        return false;
    }

    public static class ParameterizedTypeImpl implements ParameterizedType {
        private final Type raw;
        private final Type[] args;

        public ParameterizedTypeImpl(final Type raw, final Type... args) {
            this.raw = raw;
            this.args = args;
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
            return Arrays.hashCode(args) ^ Objects.hashCode(raw);
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
