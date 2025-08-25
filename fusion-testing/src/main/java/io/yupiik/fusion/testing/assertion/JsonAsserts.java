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
package io.yupiik.fusion.testing.assertion;

import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.pretty.PrettyJsonMapper;
import org.opentest4j.AssertionFailedError;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class JsonAsserts {
    private JsonAsserts() {
        // no-op
    }

    // avoid issues with random attributes order
    public static void assertJsonEquals(final String expected, final String actual) {
        try (final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            try {
                assertEquals(mapper.fromString(Object.class, expected), mapper.fromString(Object.class, actual));
            } catch (final AssertionFailedError afe) {
                final var prettifier = new PrettyJsonMapper(mapper);
                try {
                    assertEquals(  // nicer to read
                            prettifier.toString(prettifier.fromString(Object.class, expected)),
                            prettifier.toString(prettifier.fromString(Object.class, actual)));
                } catch (final AssertionFailedError afe2) {
                    throw afe2; // the one we want
                } catch (final RuntimeException re) { // something failed - maybe not even a json, just rethrow
                    throw afe;
                }
            }
        }
    }
}
