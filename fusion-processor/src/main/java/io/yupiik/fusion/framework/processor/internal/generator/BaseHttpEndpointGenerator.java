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

import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.ParsedType;
import io.yupiik.fusion.framework.processor.internal.metadata.MetadataContributorRegistry;
import io.yupiik.fusion.http.server.api.Request;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static java.util.stream.Collectors.joining;

public abstract class BaseHttpEndpointGenerator extends BaseGenerator {
    private static final Set<String> BUILTIN_JSON_TYPES = Set.of(
            int.class.getName(), long.class.getName(), boolean.class.getName(), double.class.getName(), float.class.getName(),
            String.class.getName(),
            OffsetDateTime.class.getName(), ZoneOffset.class.getName(), LocalDate.class.getName(),
            Integer.class.getName(), Long.class.getName(), Double.class.getName(), Float.class.getName(), Boolean.class.getName(),
            "io.yupiik.fusion.jsonrpc.api.PartialResponse",
            Object.class.getName(), Number.class.getName());

    protected final boolean generateBean;
    protected final ExecutableElement method;
    protected final String packageName;
    protected final String className;
    protected final Predicate<String> knownJsonModels;

    public BaseHttpEndpointGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                                     final MetadataContributorRegistry metadataContributorRegistry,
                                     final boolean generateBean, final String packageName, final String className,
                                     final ExecutableElement method, final Predicate<String> knownJsonModels) {
        super(processingEnv, elements, metadataContributorRegistry);
        this.generateBean = generateBean;
        this.method = method;
        this.packageName = packageName;
        this.className = className;
        this.knownJsonModels = knownJsonModels;
    }

    protected boolean isDirectVoid(final ParsedType returnType) {
        return "void".equals(returnType.className()) ||
                Void.class.getName().equals(returnType.className());
    }

    protected String forceCompletionStageResult(final ParsedType returnType, final String call) {
        return (switch (returnType.type()) {
            case PARAMETERIZED_TYPE -> {
                if (CompletionStage.class.getName().equals(returnType.raw()) || CompletableFuture.class.getName().equals(returnType.raw())) {
                    yield call;
                }
                // unlikely for now but once we open returned types it will be possible
                yield CompletableFuture.class.getName() + ".completedStage(" + call + ")";
            }
            case CLASS -> {
                if (isDirectVoid(returnType)) {
                    yield call + "; return " + CompletableFuture.class.getName() + ".completedStage(null);";
                }
                yield CompletableFuture.class.getName() + ".completedStage(" + call + ")";
            }
        });
    }

    protected List<String> prepareParams() {
        final var index = new AtomicInteger();
        return method.getParameters().stream()
                .map(it -> {
                    final var type = it.asType();
                    final var param = ParsedType.of(type);
                    if (isRequest(param)) {
                        return createRequestParameter();
                    }
                    if (isJson(param) || type.getAnnotation(JsonModel.class) != null) {
                        return createJsonParam(it, param, index.getAndIncrement());
                    }
                    throw new IllegalArgumentException("Invalid parameter '" + type + " " + it + "', in " + method.getEnclosingElement() + "." + method + ", only Request or JSON models are supported.");
                })
                .toList();
    }

    protected String createJsonParam(final VariableElement it, final ParsedType param, final int index) {
        return "(" + it.asType() + ") payload[" + index + "]";
    }

    protected Binding.Artifact companion(final String id, final String type, final String create) {
        final var beanId = id + ".FusionBean";
        final var binding = new Binding(packageName, "bean", "io.yupiik.fusion.framework.api.container.FusionBean<?>",
                "new io.yupiik.fusion.framework.api.container.bean.FunctionalBean<>(" + type + ".class, " +
                        findScope(method) + ".class, " + findPriority(method) + ", 0, " + metadata(method) + ", " + Binding.helperReference(0) + ", null, null, null, false)",
                List.of(new Binding.Method(0,
                        "private static " + type + " $method(io.yupiik.fusion.framework.api.RuntimeContainer container, java.util.List<io.yupiik.fusion.framework.api.Instance<?>> dependents) {\n" + create + "\n}", "")));
        return new Binding.Artifact(beanId, binding);
    }

    protected Generation compactHttp(final String suffix, final int priority, final String match, final String pattern) {
        final var id = (packageName.isBlank() ? "" : packageName + '.') + className + suffix;
        final var root = processingEnv.getTypeUtils().erasure(method.getEnclosingElement().asType()).toString();
        final var params = prepareParams();
        final var returnType = ParsedType.of(method.getReturnType());
        final var unwrapped = returnType.type() == ParsedType.Type.PARAMETERIZED_TYPE &&
                ("java.util.concurrent.CompletionStage".equals(returnType.raw()) || "java.util.concurrent.CompletableFuture".equals(returnType.raw())) ?
                new ParsedType(ParsedType.Type.CLASS, returnType.args().get(0), null, null, null) : returnType;
        final boolean json = isJson(unwrapped) || method.getReturnType().getAnnotation(JsonModel.class) != null;
        final var bodies = method.getParameters().stream().filter(p -> !isRequest(ParsedType.of(p.asType()))).toList();
        if (bodies.size() > 1) {
            throw new IllegalArgumentException("HTTP endpoints support at most one JSON body parameter, in " +
                    method.getEnclosingElement() + "." + method + ".");
        }
        final boolean mapper = json || !bodies.isEmpty();
        final var methods = new java.util.ArrayList<Binding.Method>();
        methods.add(new Binding.Method(5,
                "private static io.yupiik.fusion.http.server.impl.DefaultEndpoint $method(Object[] dependencies) {\n" +
                        "  final var root = (" + root + ") dependencies[0];\n" +
                        (mapper ? "  final var jsonMapper = (io.yupiik.fusion.json.JsonMapper) dependencies[1];\n" : "") +
                        "  final java.util.regex.Pattern pattern = " + pattern + ";\n" +
                        "  return (io.yupiik.fusion.http.server.impl.DefaultEndpoint) io.yupiik.fusion.http.server.spi.Endpoint.of(req -> $helper{6}(root, pattern, req), " +
                        "new io.yupiik.fusion.http.server.spi.DirectEndpointHandler(new java.lang.reflect.Type[]{" +
                        bodies.stream().map(p -> { final var t = ParsedType.of(p.asType()); return t.type() == ParsedType.Type.CLASS ? t.className() + ".class" : t.createParameterizedTypeImpl(); }).collect(joining(", ")) +
                        "}, (request, payload) -> $helper{7}(root, request, payload), " +
                        (mapper ? "(type, body) -> $helper{8}(jsonMapper, type, body)" : "null") + ", " +
                        (json ? "value -> $helper{9}(jsonMapper, value)" : "null") + "), " + priority + ");\n}", ""));
        methods.add(new Binding.Method(6,
                "private static boolean $method(" + root + " root, java.util.regex.Pattern pattern, io.yupiik.fusion.http.server.api.Request req) {\n" + match + "\n}", ""));
        final var call = forceCompletionStageResult(returnType, "root." + method.getSimpleName() + "(" + String.join(", ", params) + ")");
        methods.add(new Binding.Method(7,
                "private static java.util.concurrent.CompletionStage<?> $method(" + root + " root, io.yupiik.fusion.http.server.api.Request request, Object[] payload) {\n" + (call.contains("; return ") ? call : "return " + call + ";") + "\n}", ""));
        if (mapper) {
            methods.add(new Binding.Method(8,
                    "private static Object $method(io.yupiik.fusion.json.JsonMapper mapper, java.lang.reflect.Type type, Object body) {\n" +
                            "  if (body instanceof java.io.InputStream stream) { return mapper.read(type, stream); }\n" +
                            "  if (body instanceof java.io.Reader reader) { return mapper.read(type, reader); }\n" +
                            "  return mapper.read(type, new io.yupiik.fusion.json.deserialization.AvailableCharArrayReader((char[]) body));\n}", ""));
        }
        if (json) {
            methods.add(new Binding.Method(9,
                    "private static io.yupiik.fusion.http.server.api.Response $method(io.yupiik.fusion.json.JsonMapper mapper, Object value) { return io.yupiik.fusion.http.server.api.Response.of().status(200).header(\"content-type\", \"application/json;charset=utf-8\").body(mapper.toString(value)).build(); }", ""));
        }
        final var artifacts = new java.util.ArrayList<Binding.Artifact>();
        artifacts.add(new Binding.Artifact(id, new Binding(packageName, null,
                "java.util.function.Function<Object[], io.yupiik.fusion.http.server.impl.DefaultEndpoint>", "(java.util.function.Function<Object[], io.yupiik.fusion.http.server.impl.DefaultEndpoint>) " + Binding.helperReference(5), methods)));
        if (generateBean) {
            artifacts.add(companion(id, "io.yupiik.fusion.http.server.impl.DefaultEndpoint",
                    "return " + Binding.reference(id) + ".apply(new Object[]{lookup(container, " + root + ".class, dependents)" +
                            (mapper ? ", lookup(container, io.yupiik.fusion.json.JsonMapper.class, dependents)" : "") + "});"));
        }
        return new Generation(new GeneratedClass(id, null, artifacts), generateBean ? id + ".FusionBean" : null);
    }

    protected String createRequestParameter() {
        return "request";
    }

    protected boolean isRequest(final ParsedType param) {
        return param.type() == ParsedType.Type.CLASS && Request.class.getName().equals(param.className());
    }

    protected boolean isJson(final ParsedType param) {
        return switch (param.type()) {
            case CLASS -> BUILTIN_JSON_TYPES.contains(param.className()) || knownJsonModels.test(param.className());
            case PARAMETERIZED_TYPE -> // todo: test raw = collection or list or set or map or optional?
                    isJson(new ParsedType(ParsedType.Type.CLASS, param.args().get(param.args().size() - 1), null, null, null));
        };
    }

    public record Generation(BaseGenerator.GeneratedClass endpoint, String beanClassName) {
    }

}
