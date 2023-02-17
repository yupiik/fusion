package io.yupiik.fusion.persistence.impl.entity.operation;

import io.yupiik.fusion.persistence.impl.entity.SimpleFlatEntity;

import java.util.List;

// @Operation(aliases = @Operation.Alias(alias = "e", type = SimpleFlatEntity.class))
public interface MyOps {
    // @Statement("select count(*) from ${e#table}")
    long countAll();

    // @Statement("select ${e#fields} from ${e#table} order by name")
    List<SimpleFlatEntity> findAll();

    // @Statement("select ${e#fields} from ${e#table} where name = ?")
    SimpleFlatEntity findOne(String name);

    // @Statement("select ${e#fields} from ${e#table} where name = ${parameters#name}")
    SimpleFlatEntity findOneWithPlaceholders(String name);

    // @Statement("select ${e#fields} from ${e#table} where name ${parameters#name#in} order by name")
    List<SimpleFlatEntity> findByName(List<String> name);

    // @Statement("delete from ${e#table} where name like ?")
    int delete(String name);

    // @Statement("delete from ${e#table} where name like ?")
    void deleteWithoutReturnedValue(String name);
}