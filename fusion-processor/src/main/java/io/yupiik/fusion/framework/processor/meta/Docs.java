package io.yupiik.fusion.framework.processor.meta;

import java.util.List;

public record Docs(List<ClassDoc> docs) {
    public record DocItem(String name, String doc, boolean required, String ref) {
    }

    public record ClassDoc(String name, List<DocItem> items) {
    }
}
