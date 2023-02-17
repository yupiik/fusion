package io.yupiik.fusion.persistence.impl.entity;

// @Table("SIMPLE_FLAT_ENTITY")
public record SimpleFlatEntity(
        /*@Id*/ String id,
        /*@Column*/ String name,
        /* @Column(name = "SIMPLE_AGE")*/ int age) {
    // @OnInsert
    public SimpleFlatEntity onInsert() {
        return id() == null ?
                new SimpleFlatEntity(name(), name(), 1) :
                this;
    }

    // @OnLoad
    public SimpleFlatEntity onLoad() {
        return "loaded".equals(name) ?
                new SimpleFlatEntity(id() == null ? name() : id(), name(), 1) :
                this;
    }
}
