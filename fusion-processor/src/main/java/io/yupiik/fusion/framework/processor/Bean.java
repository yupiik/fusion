
package io.yupiik.fusion.framework.processor;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.Objects;
import java.util.Set;

public class Bean {
    private final Element enclosing;
    private final String name;
    private final int hash;

    public Bean(final Element enclosing, final String name) {
        this.enclosing = enclosing;
        this.name = name;

        this.hash = Objects.hash(name);
    }

    public Element enclosing() {
        return enclosing;
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return name.equals(((Bean) o).name);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    public record FieldInjection(String name, TypeMirror type,
                                 boolean list, boolean set, boolean optional,
                                 Set<Modifier> modifiers) {
    }
}