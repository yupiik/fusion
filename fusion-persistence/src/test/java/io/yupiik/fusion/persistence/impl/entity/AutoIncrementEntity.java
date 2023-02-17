package io.yupiik.fusion.persistence.impl.entity;

import java.util.StringJoiner;

// @Table("AUTO_INCREMENT_ENTITY")
public record AutoIncrementEntity(/*@Id(autoIncremented = true)*/ long id, /*@Column*/ String name) {
    @Override
    public String toString() { // tests imported from @yupiik/uship, just there to not rewrite them
        return new StringJoiner(", ", AutoIncrementEntity.class.getSimpleName() + "[", "]")
                .add("id='" + id + "'")
                .add("name='" + name + "'")
                .toString();
    }
}