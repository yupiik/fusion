/*
 * Copyright (c) 2022-2023 - Yupiik SAS - https://www.yupiik.com
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