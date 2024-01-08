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
package io.yupiik.fusion.framework.processor.internal.persistence;

import javax.lang.model.element.VariableElement;
import java.util.Collection;
import java.util.Map;

public record SimpleEntity(String table, Collection<SimpleColumn> columns) {
    public record SimpleColumn(String javaName /* null for embeddables */, String databaseName,
                               String embeddableJavaName, Map<VariableElement, SimpleColumn> columns /* for embeddables */) {}
}
