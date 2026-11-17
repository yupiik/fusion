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
package io.yupiik.fusion.framework.processor;

import io.yupiik.fusion.framework.processor.test.Compiler;
import io.yupiik.fusion.json.JsonException;
import io.yupiik.fusion.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonDocumentGeneratorTest {
    @Test
    void generatedModelsValidateCompleteDocumentsAndSkippedSubtrees(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonTypedValues").compileAndJsonAsserts((loader, mapper) -> {
            final var type = loader.apply("test.p.JsonTypedValues");
            final var valid = "{\"text\":\"answer\",\"unknown\":[{\"nested\":[true,null,{}]}]}";
            final var expected = mapper.fromString(type, valid);
            assertEquals(expected, mapper.fromString(type, valid + " \t\r\n"));
            for (final var suffix : List.of(" {}", " []", " null", " true", " 42", " \"extra\"", " x", "\0")) {
                assertThrows(JsonException.class, () -> mapper.fromString(type, valid + suffix), suffix);
            }
            try (final var compatible = mapper.as(JsonMapper.Configuring.class).orElseThrow()
                    .rejectTrailingTokens(false).build();
                 final var duplicates = compatible.as(JsonMapper.Configuring.class).orElseThrow()
                         .rejectDuplicateKeys(true).build()) {
                for (final var mode : List.of(mapper, compatible, duplicates)) {
                    for (final var invalid : List.of("", " ", "{", "{\"text\":", "{\"text\":\"answer\"",
                            "{\"unknown\":[{}", "{\"unknown\":{\"a\":[1,2]}",
                            "{\"unknown\":{\"x\":tru}}", "{\"unknown\":{\"x\":\"\\q\"}}",
                            "{\"unknown\":[1,]}", "{\"unknown\":{\"x\":1,}}")) {
                        assertThrows(JsonException.class, () -> mode.fromString(type, invalid), invalid);
                        assertThrows(JsonException.class, () -> mode.read(type, new StringReader(invalid)), invalid);
                    }
                }
                assertEquals(expected, compatible.fromString(type, valid + " garbage"));
                assertEquals(expected, duplicates.fromString(type, valid + " {}"));
            }
        });
    }
}
