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

import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;

public class Beans {
    private final Map<Type, List<FusionBean<?>>> beans = new HashMap<>();

    public Map<Type, List<FusionBean<?>>> getBeans() {
        return beans;
    }

    public void doRegister(final FusionBean<?>... beans) {
        Stream.of(beans)
                .collect(groupingBy(FusionBean::type))
                .forEach((key, list) -> this.beans.compute(key, (k, previous) -> (previous == null ?
                        list.stream() : Stream.concat(previous.stream(), list.stream()))
                        .sorted(Comparator.<FusionBean<?>, Integer>comparing(FusionBean::priority).reversed())
                        .toList()));
    }
}
