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

import io.yupiik.fusion.framework.build.api.json.JsonModel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonModel
public record JsonTypedValues(
        boolean primitiveBoolean,
        int primitiveInt,
        long primitiveLong,
        double primitiveDouble,
        Boolean boxedBoolean,
        Integer boxedInt,
        Long boxedLong,
        Double boxedDouble,
        String text,
        BigDecimal decimal,
        LocalDate date,
        LocalDateTime dateTime,
        OffsetDateTime offset,
        ZonedDateTime zoned,
        Choice choice,
        Nested nested,
        Object generic,
        List<Integer> integers,
        List<Long> longs,
        List<Double> doubles,
        List<Boolean> booleans,
        List<String> strings,
        List<BigDecimal> decimals,
        List<LocalDate> dates,
        List<Choice> choices,
        List<Nested> models,
        List<Object> objects,
        Set<Integer> integerSet,
        Set<Object> objectSet,
        Map<String, Integer> integerMap,
        Map<String, String> stringMap,
        Map<String, Boolean> booleanMap,
        Map<String, Nested> modelMap,
        Map<String, Object> objectMap,
        Map<String, List<Integer>> integerLists,
        Map<String, List<Nested>> modelLists,
        Map<String, List<Object>> objectLists) {
    @JsonModel
    public enum Choice {
        FIRST, SECOND
    }

    @JsonModel
    public record Nested(String id) {
    }
}
