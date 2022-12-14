package io.yupiik.fusion.framework.processor;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.List;

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

    public enum Type {
        CLASS, PARAMETERIZED_TYPE
    }
}