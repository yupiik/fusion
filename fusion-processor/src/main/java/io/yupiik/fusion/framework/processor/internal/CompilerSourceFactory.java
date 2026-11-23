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
package io.yupiik.fusion.framework.processor.internal;

import javax.annotation.processing.ProcessingEnvironment;

/** Java 17 implementation, replaced by the multi-release resource on Java 18 and newer. */
public final class CompilerSourceFactory {
    private CompilerSourceFactory() {
        // no-op
    }

    public static CompilerSource of(final ProcessingEnvironment environment) {
        if (environment.getElementUtils().getClass().getPackageName().startsWith("org.eclipse.jdt.")) {
            return new CompilerSource.Ecj();
        }
        return new CompilerSource.Javac(environment);
    }
}
