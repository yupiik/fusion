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

import io.yupiik.fusion.framework.build.api.http.HttpMatcher;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.metadata.MetadataContributorRegistry;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public class HttpEndpointGenerator extends BaseHttpEndpointGenerator implements Supplier<BaseHttpEndpointGenerator.Generation> {
    private static final String SUFFIX = "$FusionHttpEndpoint";

    public HttpEndpointGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                                 final MetadataContributorRegistry metadataContributorRegistry,
                                 final boolean generateBean, final String packageName, final String className,
                                 final ExecutableElement method, final Predicate<String> knownJsonModels) {
        super(processingEnv, elements, metadataContributorRegistry, generateBean, packageName, className, method, knownJsonModels);
    }

    @Override
    public Generation get() {
        final var matcher = method.getAnnotation(HttpMatcher.class);
        final var out = new StringBuilder();
        if (matcher.methods().length > 0) {
            out.append("if (!(").append(Stream.of(matcher.methods())
                    .map(m -> "\"" + escape(m) + "\".equalsIgnoreCase(req.method())").collect(joining(" || ")))
                    .append(")) { return false; }\n");
        }
        if (!"IGNORED".equals(matcher.pathMatching().name())) {
            out.append("if (req.path() == null) { return false; }\n");
        }
        final var path = "\"" + escape(matcher.path()) + "\"";
        switch (matcher.pathMatching()) {
            case IGNORED -> out.append("return true;");
            case EXACT -> out.append("return ").append(path).append(".equals(req.path());");
            case STARTS_WITH -> out.append("return req.path().startsWith(").append(path).append(");");
            case ENDS_WITH -> out.append("return req.path().endsWith(").append(path).append(");");
            case REGEX -> out.append("final var matcher = pattern.matcher(req.path());\n")
                    .append("if (!matcher.matches()) { return false; }\n")
                    .append("req.setAttribute(\"fusion.http.matcher\", matcher);\nreturn true;");
        }
        return compactHttp(SUFFIX, matcher.priority(), out.toString(),
                "REGEX".equals(matcher.pathMatching().name()) ? "java.util.regex.Pattern.compile(" + path + ")" : "null");
    }

    private static String escape(final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
