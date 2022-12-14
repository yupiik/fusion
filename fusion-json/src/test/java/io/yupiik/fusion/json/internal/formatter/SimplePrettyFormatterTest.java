package io.yupiik.fusion.json.internal.formatter;

import io.yupiik.fusion.json.internal.JsonMapperImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimplePrettyFormatterTest {
    @Test
    void format() {
        try (final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            assertEquals("""
                            {
                              "aBool": true,
                              "bigNumber": "1E+10",
                              "bigNumbers": [
                                "123",
                                "456"
                              ],
                              "booleanList": [
                                true,
                                false
                              ],
                              "date": "2022-12-06",
                              "dateList": [
                                "2022-12-06",
                                "2022-12-07"
                              ],
                              "dateTime": "2022-12-06T14:47",
                              "dateTimeList": [
                                "2022-12-06T15:19",
                                "2022-12-06T15:19:49"
                              ],
                              "doubleList": [
                                9.1,
                                10.2
                              ],
                              "generic": {
                                "gen": true
                              },
                              "genericList": [
                                {
                                  "gen": {
                                    "n": true
                                  }
                                },
                                {
                                  "gen2": {
                                    "other": 2
                                  }
                                }
                              ],
                              "intList": [
                                3,
                                4
                              ],
                              "integer": 1,
                              "lg": 3,
                              "longList": [
                                5,
                                6
                              ],
                              "mapNested": {
                                "k": {
                                  "name": "self"
                                }
                              },
                              "mapStringInt": {
                                "k": 1
                              },
                              "mapStringString": {
                                "k": "v"
                              },
                              "more": 4.5,
                              "nested": {
                                "name": "lower"
                              },
                              "nestedList": [
                                {
                                  "name": "santa"
                                },
                                {
                                  "name": "nicolas"
                                }
                              ],
                              "nullableInt": 2,
                              "offset": "2022-12-06T14:47Z",
                              "offsetList": [
                                "2022-12-06T15:19Z",
                                "2022-12-06T15:19:49Z"
                              ],
                              "simplest": "the chars",
                              "stringList": [
                                "first",
                                "second"
                              ],
                              "zoned": "2022-12-06T14:47Z",
                              "zonedList": [
                                "2022-12-06T15:19Z",
                                "2022-12-06T15:19:49Z"
                              ]
                            }""",
                    new SimplePrettyFormatter(mapper).apply("{" +
                            "\"aBool\":true,\"bigNumber\":\"1E+10\",\"bigNumbers\":[\"123\",\"456\"],\"booleanList\":[true,false]," +
                            "\"date\":\"2022-12-06\",\"dateList\":[\"2022-12-06\",\"2022-12-07\"]," +
                            "\"dateTime\":\"2022-12-06T14:47\",\"dateTimeList\":[\"2022-12-06T15:19\",\"2022-12-06T15:19:49\"]," +
                            "\"doubleList\":[9.1,10.2],\"generic\":{\"gen\":true},\"genericList\":[{\"gen\":{\"n\":true}},{\"gen2\":{\"other\":2}}]," +
                            "\"intList\":[3,4],\"integer\":1,\"lg\":3,\"longList\":[5,6],\"mapNested\":{\"k\":{\"name\":\"self\"}}," +
                            "\"mapStringInt\":{\"k\":1},\"mapStringString\":{\"k\":\"v\"},\"more\":4.5,\"nested\":{\"name\":\"lower\"}," +
                            "\"nestedList\":[{\"name\":\"santa\"},{\"name\":\"nicolas\"}],\"nullableInt\":2,\"offset\":\"2022-12-06T14:47Z\"," +
                            "\"offsetList\":[\"2022-12-06T15:19Z\",\"2022-12-06T15:19:49Z\"],\"simplest\":\"the chars\"," +
                            "\"stringList\":[\"first\",\"second\"],\"zoned\":\"2022-12-06T14:47Z\",\"zonedList\":[\"2022-12-06T15:19Z\",\"2022-12-06T15:19:49Z\"]}"));
        }
    }
}
