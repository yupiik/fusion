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
package io.yupiik.fusion.framework.processor.internal.generator;

import io.yupiik.fusion.framework.build.api.http.HttpJavaMatcher;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.metadata.MetadataContributorRegistry;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import java.util.function.Predicate;
import java.util.function.Supplier;


public class HttpJavaEndpointGenerator extends BaseHttpEndpointGenerator implements Supplier<BaseHttpEndpointGenerator.Generation> {
    private static final String SUFFIX = "$FusionHttpJavaEndpoint";

    public HttpJavaEndpointGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                                     final MetadataContributorRegistry metadataContributorRegistry,
                                     final boolean generateBean, final String packageName, final String className,
                                     final ExecutableElement method, final Predicate<String> knownJsonModels) {
        super(processingEnv, elements, metadataContributorRegistry, generateBean, packageName, className, method, knownJsonModels);
    }

    @Override
    public Generation get() {
        final var matcher = method.getAnnotation(HttpJavaMatcher.class);
        final var body = matcher.value().strip();
        return compactHttp(SUFFIX, matcher.priority(), body.startsWith("{") ? body : "return " + body + ";", "null");
    }
}
