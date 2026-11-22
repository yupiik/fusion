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
public record JsonNullableValues(
        Boolean bool, List<Boolean> boolList, Set<Boolean> boolSet, Map<String, Boolean> boolMap, Map<String, List<Boolean>> boolMapList,
        Integer integer, List<Integer> integerList, Set<Integer> integerSet, Map<String, Integer> integerMap, Map<String, List<Integer>> integerMapList,
        Long lng, List<Long> lngList, Set<Long> lngSet, Map<String, Long> lngMap, Map<String, List<Long>> lngMapList,
        Double dbl, List<Double> dblList, Set<Double> dblSet, Map<String, Double> dblMap, Map<String, List<Double>> dblMapList,
        Number number, List<Number> numberList, Set<Number> numberSet, Map<String, Number> numberMap, Map<String, List<Number>> numberMapList,
        BigDecimal decimal, List<BigDecimal> decimalList, Set<BigDecimal> decimalSet, Map<String, BigDecimal> decimalMap, Map<String, List<BigDecimal>> decimalMapList,
        String text, List<String> textList, Set<String> textSet, Map<String, String> textMap, Map<String, List<String>> textMapList,
        Nested nested, List<Nested> nestedList, Set<Nested> nestedSet, Map<String, Nested> nestedMap, Map<String, List<Nested>> nestedMapList,
        Choice choice, List<Choice> choiceList, Set<Choice> choiceSet, Map<String, Choice> choiceMap, Map<String, List<Choice>> choiceMapList,
        LocalDate date, List<LocalDate> dateList, Set<LocalDate> dateSet, Map<String, LocalDate> dateMap, Map<String, List<LocalDate>> dateMapList,
        LocalDateTime datetime, List<LocalDateTime> datetimeList, Set<LocalDateTime> datetimeSet, Map<String, LocalDateTime> datetimeMap, Map<String, List<LocalDateTime>> datetimeMapList,
        OffsetDateTime offset, List<OffsetDateTime> offsetList, Set<OffsetDateTime> offsetSet, Map<String, OffsetDateTime> offsetMap, Map<String, List<OffsetDateTime>> offsetMapList,
        ZonedDateTime zoned, List<ZonedDateTime> zonedList, Set<ZonedDateTime> zonedSet, Map<String, ZonedDateTime> zonedMap, Map<String, List<ZonedDateTime>> zonedMapList) {
    @JsonModel
    public record Nested(String id) {
    }

    @JsonModel
    public enum Choice {
        YES
    }
}
