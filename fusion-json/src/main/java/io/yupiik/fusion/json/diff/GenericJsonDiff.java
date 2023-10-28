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
package io.yupiik.fusion.json.diff;

import io.yupiik.fusion.json.patch.JsonPatchOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.yupiik.fusion.json.patch.JsonPatchOperation.Operation.add;
import static io.yupiik.fusion.json.patch.JsonPatchOperation.Operation.remove;
import static io.yupiik.fusion.json.patch.JsonPatchOperation.Operation.replace;

/**
 * Only intended for {@link Object} based model, it creates a JSON-PATCH between two instances.
 * (It supports {@code Map<String, Object>} and {@code List<Object>}.
 * The {@code Object} types are the same than in {@link io.yupiik.fusion.json.internal.codec.ObjectJsonCodec}, i.e.
 * {@link Map<String,Object>} with values in this list, {@link List<Object>} with values in this list,
 * {@link java.math.BigDecimal}, {@link String}, {@link Boolean}.
 */
public class GenericJsonDiff {
    private final Object source;
    private final Object target;

    public GenericJsonDiff(final Object source, final Object target) {
        this.source = source;
        this.target = target;
    }

    public List<JsonPatchOperation> toPatch() {
        final var patches = new ArrayList<JsonPatchOperation>();
        diff(patches, "", source, target);
        return patches;
    }

    @SuppressWarnings("unchecked")
    private void diff(final List<JsonPatchOperation> patchBuilder, final String basePath, final Object source, final Object target) {
        if (source instanceof Map<?, ?> src && target instanceof Map<?, ?> tg) {
            diffJsonObjects(patchBuilder, basePath + "/", (Map<String, ?>) src, (Map<String, ?>) tg);
        } else if (source instanceof List<?> l1 && target instanceof List<?> l2) {
            diffJsonArray(patchBuilder, basePath + "/", l1, l2);
        } else if (!source.equals(target)) {
            patchBuilder.add(new JsonPatchOperation(replace, basePath, null, target));
        }
    }

    private void diffJsonArray(final List<JsonPatchOperation> patchBuilder, final String basePath, final List<?> source, final List<?> target) {
        for (int i = 0; i < source.size(); i++) {
            final var sourceValue = source.get(i);
            if (target.size() <= i) {
                patchBuilder.add(new JsonPatchOperation(remove, basePath + i, null, null));
                continue;
            }
            diff(patchBuilder, basePath + i, sourceValue, target.get(i));
        }

        if (target.size() > source.size()) {
            for (int i = source.size(); i < target.size(); i++) {
                patchBuilder.add(new JsonPatchOperation(add, basePath + i, null, target.get(i)));
            }
        }
    }

    private void diffJsonObjects(final List<JsonPatchOperation> patchBuilder, final String basePath,
                                 final Map<String, ?> source, final Map<String, ?> target) {
        patchBuilder.addAll(source.entrySet().stream()
                .flatMap(it -> {
                    if (target.containsKey(it.getKey())) {
                        final var agg = new ArrayList<JsonPatchOperation>();
                        diff(agg, basePath + encode(it.getKey()), it.getValue(), target.get(it.getKey()));
                        return agg.stream();
                    }
                    return Stream.of(new JsonPatchOperation(remove, basePath + encode(it.getKey()), null, null));
                })
                .toList());
        patchBuilder.addAll(target.entrySet().stream()
                .filter(it -> !source.containsKey(it.getKey()))
                .map(it -> new JsonPatchOperation(add, basePath + encode(it.getKey()), null, it.getValue()))
                .toList());
    }

    private String encode(final String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }
}
