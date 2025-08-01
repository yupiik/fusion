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
package io.yupiik.fusion.framework.processor.internal.meta;

import java.util.List;

public record Docs(List<ClassDoc> docs) {
    @Override
    public int hashCode() {
        return 0; // don't be dependent of docs size
    }

    public record DocItem(String javaName, String name, String doc, boolean required, String ref, String defaultValue) {
        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    public record ClassDoc(boolean root, String name, List<DocItem> items) {
        @Override
        public int hashCode() {
            return name.hashCode();  // don't be dependent of items size
        }
    }
}
