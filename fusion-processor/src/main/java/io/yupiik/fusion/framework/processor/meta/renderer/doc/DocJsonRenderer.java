package io.yupiik.fusion.framework.processor.meta.renderer.doc;

import io.yupiik.fusion.framework.processor.meta.Docs;
import io.yupiik.fusion.json.internal.JsonStrings;

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
                                                        "\"required\":" + it.required())
                                                .filter(Objects::nonNull)
                                                .collect(joining(",", "{", "}")))
                                        .collect(joining(",")) +
                                "]")
                        .collect(joining(",")) +
                "}}";
    }
}
