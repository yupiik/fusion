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
package io.yupiik.fusion.framework.processor.internal.meta.renderer.doc;

import io.yupiik.fusion.framework.processor.internal.json.JsonStrings;
import io.yupiik.fusion.framework.processor.internal.meta.Docs;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

public class DocJsonRenderer implements Supplier<String> {
    private final Collection<Docs.ClassDoc> docs;

    public DocJsonRenderer(final Collection<Docs.ClassDoc> configurationsDocs) {
        this.docs = configurationsDocs;
    }

    @Override
    public String get() {
        return "{\"version\":1," +
                "\"classes\":{" +
                docs.stream()
                        .collect(toMap(Docs.ClassDoc::name, Docs.ClassDoc::items))
                        .entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> "" +
                                "\"" + e.getKey() + "\":[" +
                                e.getValue().stream()
                                        .sorted(comparing(Docs.DocItem::name))
                                        .map(it -> Stream.of(
                                                        it.ref() != null ? "\"ref\":" + JsonStrings.escape(it.ref()) : null,
                                                        "\"name\":" + JsonStrings.escape(it.name()),
                                                        it.doc() != null ? "\"documentation\":" + JsonStrings.escape(it.doc()) : null,
                                                        it.defaultValue() != null ? "\"defaultValue\":" + it.defaultValue() : null,
                                                        "\"required\":" + it.required())
                                                .filter(Objects::nonNull)
                                                .collect(joining(",", "{", "}")))
                                        .collect(joining(",")) +
                                "]")
                        .collect(joining(",")) +
                "}}";
    }
}
