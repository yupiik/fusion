package test.p;

import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.fusion.framework.build.api.json.JsonOthers;
import io.yupiik.fusion.framework.build.api.json.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface JsonRecords {
    @JsonModel
    public record StringHolder(String name) {
    }

    @JsonModel
    public record AllInOne(
            boolean aBool,
            @JsonProperty("bigNumber") BigDecimal bigDecimal,
            int integer,
            Integer nullableInt,
            long lg,
            double more,
            String simplest,
            LocalDate date,
            LocalDateTime dateTime,
            OffsetDateTime offset,
            ZonedDateTime zoned,
            Object generic,
            StringHolder nested,
            List<Boolean> booleanList,
            @JsonProperty("bigNumbers") List<BigDecimal> bigDecimalList,
            List<Integer> intList,
            Collection<Long> longList,
            List<Double> doubleList,
            Set<String> stringList,
            List<LocalDate> dateList,
            List<LocalDateTime> dateTimeList,
            List<OffsetDateTime> offsetList,
            List<ZonedDateTime> zonedList,
            List<Object> genericList,
            List<StringHolder> nestedList,
            Map<String, String> mapStringString,
            Map<String, Integer> mapStringInt,
            Map<String, StringHolder> mapNested,
            @JsonOthers Map<String, Object> others) {
    }

    @JsonModel
    public record StrongTyping(
            boolean aBool,
            @JsonProperty("bigNumber") BigDecimal bigDecimal,
            int integer,
            Integer nullableInt,
            long lg,
            double more,
            String simplest,
            LocalDate date,
            LocalDateTime dateTime,
            OffsetDateTime offset,
            ZonedDateTime zoned,
            Object generic,
            StringHolder nested,
            List<Boolean> booleanList,
            @JsonProperty("bigNumbers") List<BigDecimal> bigDecimalList,
            List<Integer> intList,
            Collection<Long> longList,
            List<Double> doubleList,
            Set<String> stringList,
            List<LocalDate> dateList,
            List<LocalDateTime> dateTimeList,
            List<OffsetDateTime> offsetList,
            List<ZonedDateTime> zonedList,
            List<Object> genericList,
            List<StringHolder> nestedList,
            Map<String, String> mapStringString,
            Map<String, Integer> mapStringInt,
            Map<String, StringHolder> mapNested) {
    }
}
