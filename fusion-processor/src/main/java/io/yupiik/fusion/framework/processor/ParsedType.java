package io.yupiik.fusion.framework.processor;

import io.yupiik.fusion.framework.api.container.Types;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.List;

import static java.util.stream.Collectors.joining;

public record ParsedType(Type type, String className, String raw, List<String> args) {
    public static ParsedType of(final TypeMirror type) {
        if (type instanceof DeclaredType dt && !dt.getTypeArguments().isEmpty()) {
            final var element = dt.asElement();
            return new ParsedType(
                    Type.PARAMETERIZED_TYPE, null,
                    element instanceof TypeElement te ? te.getQualifiedName().toString() : element.toString(),
                    dt.getTypeArguments().stream()
                            .map(TypeMirror::toString) // simplistic for now
                            .toList());
        }
        return new ParsedType(Type.CLASS, type.toString(), null, null);
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

    public enum Type {
        CLASS, PARAMETERIZED_TYPE
    }
}