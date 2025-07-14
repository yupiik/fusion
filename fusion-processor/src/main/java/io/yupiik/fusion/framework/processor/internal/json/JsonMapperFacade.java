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
package io.yupiik.fusion.framework.processor.internal.json;

import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.internal.JsonMapperImpl;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JsonMapperFacade {
    private JsonMapper mapper;

    @SuppressWarnings("unchecked")
    public Map<String, Object> read(final String source) {
        ensureInit();
        return (Map<String, Object>) mapper.fromString(Object.class, source);
    }

    public String write(final Object source) {
        ensureInit();
        return mapper.toString(source);
    }

    private void ensureInit() {
        if (mapper == null) {
            mapper = new JsonMapperImpl(List.of(), c -> Optional.empty());
        }
    }

    public JsonMapper getMapper() {
        return mapper;
    }
}
