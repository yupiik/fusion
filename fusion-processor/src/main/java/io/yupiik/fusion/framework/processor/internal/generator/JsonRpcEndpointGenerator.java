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
import io.yupiik.fusion.framework.build.api.jsonrpc.JsonRpc;
import io.yupiik.fusion.framework.build.api.jsonrpc.JsonRpcParam;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.GeneratedJsonSchema;
import io.yupiik.fusion.framework.processor.internal.ParsedType;
import io.yupiik.fusion.framework.processor.internal.meta.JsonSchema;
import io.yupiik.fusion.framework.processor.internal.meta.PartialOpenRPC;
import io.yupiik.fusion.framework.processor.internal.metadata.MetadataContributorRegistry;
import io.yupiik.fusion.json.internal.codec.ObjectJsonCodec;
import io.yupiik.fusion.json.internal.parser.BufferProvider;
import io.yupiik.fusion.json.internal.parser.JsonParser;
import io.yupiik.fusion.json.serialization.JsonCodec;
import io.yupiik.fusion.jsonrpc.api.PartialResponse;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

public class JsonRpcEndpointGenerator extends BaseHttpEndpointGenerator implements Supplier<BaseHttpEndpointGenerator.Generation> {
    private static final String SUFFIX = "$FusionJsonRpcMethod";

    private final PartialOpenRPC openRPC;
    private final Map<String, GeneratedJsonSchema> allJsonSchemas;
    private final List<ParamMeta> jsonRpcParams = new ArrayList<>();
    private final StringBuilder conversions = new StringBuilder();

    public JsonRpcEndpointGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                                    final MetadataContributorRegistry metadataContributorRegistry,
                                    final boolean beanForJsonRpcEndpoints, final String packageName, final String className,
                                    final ExecutableElement method, final Predicate<String> knownJsonModels,
                                    final PartialOpenRPC openRPC, final Map<String, GeneratedJsonSchema> allJsonSchemas) {
        super(processingEnv, elements, metadataContributorRegistry, beanForJsonRpcEndpoints, packageName, className, method, knownJsonModels);
        this.openRPC = openRPC;
        this.allJsonSchemas = allJsonSchemas;
    }

    @Override
    public BaseHttpEndpointGenerator.Generation get() {
        final var returnType = unwrapReturnedType(method.getReturnType(), ParsedType.of(method.getReturnType()));
        final boolean isReturnTypeJson = isJson(returnType) || method.getReturnType().getAnnotation(JsonModel.class) != null;
        final boolean isVoid = isVoidLike(returnType);
        if (!isVoid && !isReturnTypeJson) {
            throw new IllegalArgumentException("JSON-RPC method must return a JSON instance, invalid type: '" + method.getReturnType() +
                    "'; annotate exported models with @JsonModel and ensure they are on the compilation classpath");
        }

        final var packagePrefix = packageName == null || packageName.isBlank() ? "" : (packageName + '.');
        final var endpoint = method.getAnnotation(JsonRpc.class);
        final var priority = findPriority(method);
        final var methodClassName = className + SUFFIX;

        // drop the method name to get the enclosing one
        final var enclosingClassName = processingEnv.getTypeUtils().erasure(method.getEnclosingElement().asType()).toString();
        final var params = prepareParams();

        if (openRPC != null) { // todo: add missing metadata (@JsonRpcError etc)
            final var counter = new AtomicInteger();
            openRPC.methods().computeIfAbsent(endpoint.value(), k -> new PartialOpenRPC.Method(
                    endpoint.value(),
                    endpoint.documentation(),
                    endpoint.documentation(),
                    method.getAnnotation(Deprecated.class) != null ? true : null,
                    "either",
                    params.stream()
                            .filter(it -> !"context.request()".equals(it))
                            .map(it -> {
                                final var meta = jsonRpcParams.get(counter.getAndIncrement());
                                final var paramElement = method.getParameters().stream().filter(p -> !isRequest(ParsedType.of(p.asType()))).toList().get(counter.get() - 1);
                                final var jsonRpcParam = ofNullable(paramElement.getAnnotation(JsonRpcParam.class));
                                final var required = jsonRpcParam.map(JsonRpcParam::required).filter(b -> b).orElse(null);
                                return new PartialOpenRPC.Value(
                                        meta.name(),
                                        jsonRpcParam.map(JsonRpcParam::documentation).filter(Predicate.not(String::isBlank)).orElse(null),
                                        required,
                                        paramElement.getAnnotation(Deprecated.class) != null ? true : null,
                                        getSchema(meta.type(), required));
                            })
                            .toList(),
                    new PartialOpenRPC.Value(
                            "result",
                            null, null, null,
                            isVoid ?
                                    new JsonSchema(null, null, "null", true, null, null, null, null, null) :
                                    getSchema(new EnrichedParsedType(unwrapResultType(method.getReturnType())), null)),
                    Stream.of(endpoint.errors())
                            .map(it -> new PartialOpenRPC.ErrorValue(it.code(), it.documentation(), null))
                            .toList()));
        }

        final var id = packagePrefix + methodClassName;
        final var call = forceCompletionStageResult(ParsedType.of(method.getReturnType()), "root." + method.getSimpleName() + "(" + String.join(", ", params) + ")");
        final var methods = List.of(
                new Binding.Method(5,
                        "private static io.yupiik.fusion.jsonrpc.impl.DefaultJsonRpcMethod $method(java.lang.Object[] dependencies) {\n" +
                                "final var root = (" + enclosingClassName + ") dependencies[0];\n" +
                                "final var jsonMapper = (io.yupiik.fusion.json.JsonMapper) dependencies[1];\n" +
                                "return new io.yupiik.fusion.jsonrpc.impl.DefaultJsonRpcMethod(" + priority + ", \"" + endpoint.value().replace("\"", "\\\"").replace("\n", "\\n") + "\", context -> $helper{6}(root, jsonMapper, context), " + isVoid + ", " + metadata(method) + "); }", ""),
                new Binding.Method(6,
                        "private static java.util.concurrent.CompletionStage<?> $method(" + enclosingClassName + " root, io.yupiik.fusion.json.JsonMapper jsonMapper, io.yupiik.fusion.jsonrpc.impl.JsonRpcMethod.Context context) {\n" + conversions +
                                (call.contains("; return ") ? call : "return " + call + ";") + "\n}", ""));
        final var artifacts = new ArrayList<Binding.Artifact>();
        artifacts.add(new Binding.Artifact(id, new Binding(packageName, null,
                "java.util.function.Function<java.lang.Object[], io.yupiik.fusion.jsonrpc.impl.DefaultJsonRpcMethod>", "(java.util.function.Function<java.lang.Object[], io.yupiik.fusion.jsonrpc.impl.DefaultJsonRpcMethod>) " + Binding.helperReference(5), methods)));
        if (generateBean) {
            artifacts.add(companion(id, "io.yupiik.fusion.jsonrpc.impl.DefaultJsonRpcMethod",
                    "return " + Binding.reference(id) + ".apply(new java.lang.Object[]{lookup(container, " + enclosingClassName + ".class, dependents), lookup(container, io.yupiik.fusion.json.JsonMapper.class, dependents)});"));
        }
        return new Generation(new GeneratedClass(id, null, artifacts), generateBean ? id + ".FusionBean" : null);
    }

    private boolean isVoidLike(final ParsedType returnType) {
        return isDirectVoid(returnType) ||
                (returnType.type() == ParsedType.Type.PARAMETERIZED_TYPE &&
                        returnType.args().size() == 1 &&
                        (CompletionStage.class.getName().equals(returnType.raw()) || CompletableFuture.class.getName().equals(returnType.raw())) &&
                        Void.class.getName().equals(returnType.args().get(0)));
    }

    private ParsedType unwrapReturnedType(final TypeMirror typeMirror, final ParsedType type) {
        return type.type() == ParsedType.Type.PARAMETERIZED_TYPE && PartialResponse.class.getName().equals(type.raw()) ?
                ParsedType.of(((DeclaredType) typeMirror).getTypeArguments().get(0)) :
                type;
    }

    /**
     * Unlike {@link #unwrapReturnedType(TypeMirror, ParsedType)} - which the generated invocation relies on and
     * must keep seeing the declared type - this describes what is actually serialized: the value the wrappers
     * resolve to. {@code JsonRpcHandler} awaits the promise then unwraps a {@link PartialResponse}, so
     * {@code CompletionStage<PartialResponse<T>>} is a {@code T} on the wire.
     *
     * @param typeMirror the declared returned type.
     * @return the type of the JSON-RPC {@code result} value.
     */
    private ParsedType unwrapResultType(final TypeMirror typeMirror) {
        final var type = ParsedType.of(typeMirror);
        if (type.type() == ParsedType.Type.PARAMETERIZED_TYPE && type.args().size() == 1 && isResultWrapper(type.raw())) {
            return unwrapResultType(((DeclaredType) typeMirror).getTypeArguments().get(0));
        }
        return type;
    }

    private boolean isResultWrapper(final String raw) {
        return CompletionStage.class.getName().equals(raw) ||
                CompletableFuture.class.getName().equals(raw) ||
                PartialResponse.class.getName().equals(raw);
    }

    // Reuse an available schema or reference a compiler-discovered model independently of schema output.
    @SuppressWarnings("unchecked")
    private JsonSchema generateFullJsonSchema(final String name) {
        if (!knownJsonModels.test(name)) {
            throw new IllegalArgumentException("Unknown JSON model '" + name + "'; annotate exported models with @JsonModel and ensure they are on the compilation classpath");
        }

        final var generatedJsonSchema = allJsonSchemas.get(name);
        if (generatedJsonSchema != null) {
            if (generatedJsonSchema.content() != null) {
                return generatedJsonSchema.content();
            }

            final var objectJsonCodec = new ObjectJsonCodec();
            try (final var parser = new JsonParser(new StringReader(generatedJsonSchema.raw()), 81920, new BufferProvider(81920, 2), true)) {
                final var read = (Map<String, Object>) objectJsonCodec.read(new JsonCodec.DeserializationContext(parser, c -> {
                    throw new IllegalArgumentException("missing codec: " + c);
                }));
                return toSchema(read);
            } catch (final IOException | RuntimeException e) { // mock the schema
                return new JsonSchema(null, name, null, null, null, null, null, null, null);
            }
        }

        // create a ref
        return new JsonSchema(name, null, null, null, null, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private JsonSchema toSchema(final Map<String, Object> read) {
        return new JsonSchema(
                ofNullable(read.get("$ref")).map(String::valueOf).orElse(null),
                ofNullable(read.get("$id")).map(String::valueOf).orElse(null),
                ofNullable(read.get("type")).map(String::valueOf).orElse(null),
                ofNullable(read.get("nullable")).map(Boolean.class::cast).orElse(null),
                ofNullable(read.get("format")).map(String::valueOf).orElse(null),
                ofNullable(read.get("pattern")).map(String::valueOf).orElse(null),
                read.get("additionalProperties"),
                ofNullable(read.get("properties"))
                        .map(it -> ((Map<String, Object>) it).entrySet().stream()
                                .collect(toMap(Map.Entry::getKey, i -> toSchema((Map<String, Object>) i.getValue()))))
                        .orElse(null),
                ofNullable(read.get("items")).map(Map.class::cast).map(it -> toSchema((Map<String, Object>) it)).orElse(null),
                ofNullable(read.get("title")).map(String::valueOf).orElse(null),
                ofNullable(read.get("description")).map(String::valueOf).orElse(null),
                ofNullable(read.get("enum")).map(List.class::cast).orElse(null),
                ofNullable(read.get("required")).map(List.class::cast).orElse(null));
    }

    private JsonSchema getSchema(final EnrichedParsedType enrichedParsedType, final Boolean required) {
        final var type = enrichedParsedType.type();
        switch (type.type()) {
            case CLASS -> {
                return switch (type.className()) {
                    case "java.lang.Number" -> new JsonSchema(null, null, "number", !(required != null && required), null, null, null, null, null);
                    case "java.lang.Object" -> new JsonSchema(null, null, "object", !(required != null && required), null, null, true, null, null);
                    case "boolean", "java.lang.Boolean" ->
                            new JsonSchema(null, null, "boolean", !(required != null && required) && !"boolean".equals(type.className()), null, null, null, null, null);
                    case "int", "java.lang.Integer" ->
                            new JsonSchema(null, null, "integer", !(required != null && required) && !"int".equals(type.className()), "int32", null, null, null, null);
                    case "long", "java.lang.Long" ->
                            new JsonSchema(null, null, "integer", !(required != null && required) && !"long".equals(type.className()), "int64", null, null, null, null);
                    case "double", "float", "java.lang.Double", "java.lang.Float" ->
                            new JsonSchema(null, null, "number", !(required != null && required) && !Character.isLowerCase(type.className().charAt(0)), "double", null, null, null, null);
                    case "java.time.OffsetDateTime", "java.time.ZoneOffset" ->
                            new JsonSchema(null, null, "string", !(required != null && required), "date-time", null, null, null, null);
                    case "java.time.LocalDate" ->
                            new JsonSchema(null, null, "string", !(required != null && required), "date", null, null, null, null);
                    case "java.lang.String" -> new JsonSchema(null, null, "string", !(required != null && required), null, null, null, null, null);
                    default -> {
                        if (type.enumValues() != null) {
                            yield new JsonSchema(
                                    null, null,
                                    "string",
                                    required != null && required ? false : null, null, null, null, null, null, null, null,
                                    type.enumValues());
                        }
                        final var schema = openRPC.schemas().computeIfAbsent(
                                type.className(),
                                k -> generateFullJsonSchema(type.className()));
                        final var id = schema.id() == null ? schema.ref() : schema.id();
                        yield new JsonSchema(
                                "#/schemas/" + requireNonNull(id, "missing id/ref: " + id),
                                null, null, (required != null && required) ? false : null, null, null, null, null, null);
                    }
                };
            }
            case PARAMETERIZED_TYPE -> {
                switch (type.raw()) {
                    case "java.util.Collection", "java.util.List", "java.util.Set" -> {
                        return new JsonSchema(
                                null, null,
                                "array", !(required != null && required),
                                null, null,
                                null, null,
                                switch (type.args().get(0)) {
                                    case "java.lang.Number" -> new JsonSchema(null, null, "number", !(required != null && required), null, null, null, null, null);
                                    case "java.lang.Object" ->
                                            new JsonSchema(null, null, "object", !(required != null && required), null, null, true, null, null);
                                    case "boolean", "java.lang.Boolean" ->
                                            new JsonSchema(null, null, "boolean", !(required != null && required) && !"boolean".equals(type.args().get(0)), null, null, null, null, null);
                                    case "int", "java.lang.Integer" ->
                                            new JsonSchema(null, null, "integer", !(required != null && required) && !"int".equals(type.args().get(0)), "int32", null, null, null, null);
                                    case "long", "java.lang.Long" ->
                                            new JsonSchema(null, null, "integer", !(required != null && required) && !"long".equals(type.args().get(0)), "int64", null, null, null, null);
                                    case "double", "float", "java.lang.Double", "java.lang.Float" ->
                                            new JsonSchema(null, null, "number", !(required != null && required) && !Character.isLowerCase(type.args().get(0).charAt(0)), "double", null, null, null, null);
                                    case "java.lang.String" ->
                                            new JsonSchema(null, null, "string", !(required != null && required), null, null, null, null, null);
                                    default -> {
                                        final var schema = openRPC.schemas().computeIfAbsent(
                                                type.args().get(0),
                                                k -> generateFullJsonSchema(type.args().get(0)));
                                        final var id = schema.id() == null ? schema.ref() : schema.id();
                                        yield new JsonSchema(
                                                "#/schemas/" + requireNonNull(id, "missing id/ref: " + schema),
                                                null, null, (required != null && required) ? false : null, null, null, null, null, null);
                                    }
                                });
                    }
                    case "java.util.Map" -> {
                        return new JsonSchema(
                                null, null,
                                "object", !(required != null && required),
                                null, null,
                                (switch (type.args().get(1)) {
                                    case "java.lang.Number" -> new JsonSchema(null, null, "number", !(required != null && required), null, null, null, null, null);
                                    case "java.lang.Object" ->
                                            new JsonSchema(null, null, "object", !(required != null && required), null, null, true, null, null);
                                    case "boolean", "java.lang.Boolean" ->
                                            new JsonSchema(null, null, "boolean", !(required != null && required) && !"boolean".equals(type.args().get(1)), null, null, null, null, null);
                                    case "int", "java.lang.Integer" ->
                                            new JsonSchema(null, null, "integer", !(required != null && required) && !"int".equals(type.args().get(1)), "int32", null, null, null, null);
                                    case "long", "java.lang.Long" ->
                                            new JsonSchema(null, null, "integer", !(required != null && required) && !"long".equals(type.args().get(1)), "int64", null, null, null, null);
                                    case "double", "float", "java.lang.Double", "java.lang.Float" ->
                                            new JsonSchema(null, null, "number", !(required != null && required) && !Character.isLowerCase(type.args().get(1).charAt(0)), "double", null, null, null, null);
                                    case "java.lang.String" ->
                                            new JsonSchema(null, null, "string", true, null, null, null, null, null);
                                    default -> {
                                        final var schema = openRPC.schemas().computeIfAbsent(
                                                type.args().get(1),
                                                k -> generateFullJsonSchema(type.args().get(1)));
                                        final var id = schema.id() == null ? schema.ref() : schema.id();
                                        yield new JsonSchema(
                                                "#/schemas/" + requireNonNull(id, "missing id/ref: " + schema),
                                                null, null, (required != null && required) ? false : null, null, null, null, null, null);
                                    }
                                }).asMap(),
                                null, null);
                    }
                    case "java.util.Optional", "java.util.concurrent.CompletionStage" -> {
                        return new JsonSchema(
                                null, null,
                                "object", !(required != null && required),
                                null, null,
                                (switch (type.args().get(0)) {
                                    case "java.lang.Number" -> new JsonSchema(null, null, "number", !(required != null && required), null, null, null, null, null);
                                    case "java.lang.Object" ->
                                            new JsonSchema(null, null, "object", !(required != null && required), null, null, true, null, null);
                                    case "boolean", "java.lang.Boolean" ->
                                            new JsonSchema(null, null, "boolean", !(required != null && required) && !"boolean".equals(type.args().get(0)), null, null, null, null, null);
                                    case "int", "java.lang.Integer" ->
                                            new JsonSchema(null, null, "integer", !(required != null && required) && !"int".equals(type.args().get(0)), "int32", null, null, null, null);
                                    case "long", "java.lang.Long" ->
                                            new JsonSchema(null, null, "integer", !(required != null && required) && !"long".equals(type.args().get(0)), "int64", null, null, null, null);
                                    case "double", "float", "java.lang.Double", "java.lang.Float" ->
                                            new JsonSchema(null, null, "number", !(required != null && required) && !Character.isLowerCase(type.args().get(0).charAt(0)), "double", null, null, null, null);
                                    case "java.lang.String" ->
                                            new JsonSchema(null, null, "string", !(required != null && required), null, null, null, null, null);
                                    default -> {
                                        final var schema = openRPC.schemas().computeIfAbsent(
                                                type.args().get(0),
                                                k -> generateFullJsonSchema(type.args().get(0)));
                                        final var id = schema.id() == null ? schema.ref() : schema.id();
                                        yield new JsonSchema(
                                                "#/schemas/" + requireNonNull(id, "missing id/ref: " + schema),
                                                null, null, (required != null && required) ? false : null, null, null, null, null, null);
                                    }
                                }).asMap(),
                                null, null);
                    }
                    default ->
                            throw new IllegalArgumentException("For now generic parameterized type in JSON-RPC methods are not supported, only collections and optionals.");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported " + type);
        }
    }

    @Override
    protected String createJsonParam(final VariableElement it, final ParsedType param, final int index) {
        final var conf = it.getAnnotation(JsonRpcParam.class);
        final var name = ofNullable(conf).map(JsonRpcParam::value).filter(n -> !n.isBlank()).orElseGet(() -> it.getSimpleName().toString());
        final var literal = "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
        jsonRpcParams.add(new ParamMeta(name, new EnrichedParsedType(param)));
        final var value = "value" + index;
        final var argument = "argument" + index;
        final var helper = "io.yupiik.fusion.jsonrpc.impl.JsonRpcParameters";
        conversions.append("final java.lang.Object ").append(value).append(" = ").append(helper).append(".findParameter(context, ")
                .append(literal).append(", ").append(index).append(", ").append(conf != null && conf.required()).append(");\n");
        if (param.type() == ParsedType.Type.CLASS) {
            conversions.append("final ").append(it.asType()).append(' ').append(argument).append(" = ").append(scalar(param.className(), value)).append(";\n");
        } else {
            final var raw = param.raw();
            switch (raw) {
                case "java.util.List", "java.util.Collection", "java.util.Set" -> {
                    final var item = param.args().get(0);
                    final var set = "java.util.Set".equals(raw);
                    conversions.append("final ").append(it.asType()).append(' ').append(argument).append(";\n")
                            .append("if (").append(value).append(" == null) { ").append(argument).append(" = null; } else {\n")
                            .append("final var converted = new ").append(set ? "java.util.HashSet" : "java.util.ArrayList").append('<').append(item).append(">();\n")
                            .append("for (final java.lang.Object item : ").append(helper).append(".failIfNotCollection(").append(literal).append(", ").append(value).append(")) { converted.add(").append(elementValue(item, "item")).append("); }\n")
                            .append(argument).append(" = ").append(set ? "converted" : "java.util.Collections.unmodifiableList(converted)").append(";\n}\n");
                }
                case "java.util.Map" -> {
                    if (!"java.lang.String".equals(param.args().get(0))) {
                        throw new IllegalArgumentException("Only Map<String, X> are supported in root JSON-RPC parameters");
                    }
                    if (!Set.of("java.lang.Object", "java.lang.Boolean", "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double", "java.lang.Number", "java.lang.String").contains(param.args().get(1))) {
                        // These types used a whole-map mapper roundtrip, including nullable and nested values.
                        conversions.append("final ").append(it.asType()).append(' ').append(argument).append(" = ")
                                .append(scalar(it.asType().toString(), value)).append(";\n");
                        break;
                    }
                    conversions.append("final ").append(it.asType()).append(' ').append(argument).append(";\n")
                            .append("if (").append(value).append(" == null) { ").append(argument).append(" = null; } else {\n")
                            .append("final var converted = new java.util.HashMap<java.lang.String, ").append(param.args().get(1)).append(">();\n")
                            .append("for (final var entry : ").append(helper).append(".failIfNotMap(").append(literal).append(", ").append(value).append(").entrySet()) { converted.put(entry.getKey(), java.util.Objects.requireNonNull(")
                            .append(elementValue(param.args().get(1), "entry.getValue()")).append(")); }\n")
                            .append(argument).append(" = converted;\n}\n");
                }
                case "java.util.Optional" -> conversions.append("final ").append(it.asType()).append(' ').append(argument)
                        .append(" = java.util.Optional.ofNullable(").append(elementValue(param.args().get(0), value)).append(");\n");
                default -> throw new IllegalArgumentException("Unsupported JSON-RPC parameter: " + it.asType());
            }
        }
        return argument;
    }

    private String elementValue(final String type, final String value) {
        return "java.lang.Boolean".equals(type) ?
                value + " == null ? null : java.lang.Boolean.TRUE.equals(" + value + ")" : scalar(type, value);
    }

    private String scalar(final String type, final String value) {
        return switch (type) {
            case "java.lang.Object" -> value;
            case "boolean" -> "java.lang.Boolean.TRUE.equals(" + value + ")";
            case "java.lang.Boolean" -> "(java.lang.Boolean) " + value;
            case "int", "long", "double", "float", "java.lang.Integer", "java.lang.Long", "java.lang.Double", "java.lang.Float" -> {
                final var primitive = switch (type) {
                    case "int", "java.lang.Integer" -> "int";
                    case "long", "java.lang.Long" -> "long";
                    case "double", "java.lang.Double" -> "double";
                    default -> "float";
                };
                yield value + " == null ? " + (!type.contains(".") ? "0" : "null") + " : ((java.lang.Number) " + value + ")." + primitive + "Value()";
            }
            case "java.lang.Number" -> "(java.lang.Number) " + value;
            case "java.lang.String" -> "(java.lang.String) " + value;
            default -> value + " == null ? null : jsonMapper.fromString(" + typeLiteral(type) + ", jsonMapper.toString(" + value + "))";
        };
    }

    private String typeLiteral(final String type) {
        final int generic = type.indexOf('<');
        if (generic < 0) {
            return type + ".class";
        }
        final var arguments = new ArrayList<String>();
        int depth = 0;
        int start = generic + 1;
        for (int i = start; i < type.length() - 1; i++) {
            final char c = type.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                arguments.add(typeLiteral(type.substring(start, i).trim()));
                start = i + 1;
            }
        }
        arguments.add(typeLiteral(type.substring(start, type.length() - 1).trim()));
        return "new io.yupiik.fusion.framework.api.container.Types.ParameterizedTypeImpl(" + type.substring(0, generic) + ".class, " + String.join(", ", arguments) + ")";
    }

    @Override
    protected String createRequestParameter() {
        return "context.request()";
    }

    private record EnrichedParsedType(ParsedType type) { // potentially get the Element or TypeMirror later
    }

    private record ParamMeta(String name, EnrichedParsedType type) {
    }
}
