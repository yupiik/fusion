package test.p.persistence;

import io.yupiik.fusion.framework.build.api.persistence.Column;
import io.yupiik.fusion.framework.build.api.persistence.Id;
import io.yupiik.fusion.framework.build.api.persistence.OnInsert;
import io.yupiik.fusion.framework.build.api.persistence.OnLoad;
import io.yupiik.fusion.framework.build.api.persistence.Table;

@Table("SIMPLE_FLAT_ENTITY")
public record SimpleFlatEntity(
        @Id String id,
        @Column String name,
        @Column byte[] arr,
        @Column(name = "SIMPLE_AGE") int age) {
    @OnInsert
    public SimpleFlatEntity onInsert() {
        return id() == null ?
                new SimpleFlatEntity(name(), name(),null, 1) :
                this;
    }

    @OnLoad
    public SimpleFlatEntity onLoad() {
        return "loaded".equals(name) ?
                new SimpleFlatEntity(id() == null ? name() : id(), name(), null,1) :
                this;
    }
}