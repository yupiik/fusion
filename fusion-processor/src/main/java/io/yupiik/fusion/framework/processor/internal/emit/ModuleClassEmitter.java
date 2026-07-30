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
package io.yupiik.fusion.framework.processor.internal.emit;

import javax.annotation.processing.ProcessingEnvironment;
import java.io.IOException;
import java.util.List;

/**
 * Emits the {@code FusionGeneratedModule} class.
 * The default is to generate a source compiled by javac but when the build runs on a JVM
 * supporting the {@code java.lang.classfile} API (24+) the class can be written directly
 * as bytecode ({@code META-INF/versions/24} implementation) which is faster since it
 * bypasses the javac pipeline and does not trigger an annotation processing round.
 */
public interface ModuleClassEmitter {
    /**
     * @param environment the processing environment (filer and target version source).
     * @param moduleFqn the fully qualified name of the module class.
     * @param beans the sorted bean class names in their source form (nested beans use a dot separator, e.g. {@code X$FusionJsonCodec.FusionBean}).
     * @param listeners the sorted listener class names.
     */
    void emit(ProcessingEnvironment environment, String moduleFqn, List<String> beans, List<String> listeners) throws IOException;
}
