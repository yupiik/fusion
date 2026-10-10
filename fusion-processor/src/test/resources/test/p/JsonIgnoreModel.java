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
package test.p;

import io.yupiik.fusion.framework.build.api.json.JsonIgnore;
import io.yupiik.fusion.framework.build.api.json.JsonModel;

import java.util.List;
import java.util.Map;

public interface JsonIgnoreModel {
    @JsonModel
    public record AllPrimitives(
            boolean visibleBoolean,
            @JsonIgnore boolean hiddenBoolean,
            int visibleInt,
            @JsonIgnore int hiddenInt,
            long visibleLong,
            @JsonIgnore long hiddenLong,
            double visibleDouble,
            @JsonIgnore double hiddenDouble) {
    }

    @JsonModel
    public record AllObjects(
            String visibleString,
            @JsonIgnore String hiddenString,
            Integer visibleInteger,
            @JsonIgnore Integer hiddenInteger,
            List<String> visibleList,
            @JsonIgnore List<String> hiddenList,
            Map<String, Integer> visibleMap,
            @JsonIgnore Map<String, Integer> hiddenMap,
            StringHolder visibleNested,
            @JsonIgnore StringHolder hiddenNested,
            @JsonIgnore Object hiddenObject) {
    }

    @JsonModel
    public record StringHolder(String name) {
    }
}