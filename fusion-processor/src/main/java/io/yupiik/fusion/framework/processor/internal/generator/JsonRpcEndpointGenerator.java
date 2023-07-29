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
package io.yupiik.fusion.framework.processor.internal.generator;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.fusion.framework.build.api.jsonrpc.JsonRpc;
import io.yupiik.fusion.framework.build.api.jsonrpc.JsonRpcParam;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.ParsedType;
import io.yupiik.fusion.framework.processor.internal.meta.JsonSchema;
import io.yupiik.fusion.framework.processor.internal.meta.PartialOpenRPC;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.jsonrpc.api.PartialResponse;
import io.yupiik.fusion.jsonrpc.impl.DefaultJsonRpcMethod;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

public class JsonRpcEndpointGenerator extends BaseHttpEndpointGenerator implements Supplier<BaseHttpEndpointGenerator.Generation> {
    private static final String SUFFIX = "$FusionJsonRpcMethod";

    private final PartialOpenRPC openRPC;
    private final Map<String, JsonSchema> allJsonSchemas;
    private final List<ParamMeta> jsonRpcParams = new ArrayList<>();

    public JsonRpcEndpointGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                                    final boolean beanForJsonRpcEndpoints, final String packageName, final String className,
                                    final ExecutableElement method, final Set<String> knownJsonModels,
                                    final PartialOpenRPC openRPC, final Map<String, JsonSchema> allJsonSchemas) {
        super(processingEnv, elements, beanForJsonRpcEndpoints, packageName, className, method, knownJsonModels);
        this.openRPC = openRPC;
        this.allJsonSchemas = allJsonSchemas;
    }

    @Override
    public BaseHttpEndpointGenerator.Generation get() {
        final var returnType = unwrapReturnedType(method.getReturnType(), ParsedType.of(method.getReturnType()));
        final boolean isReturnTypeJson = isJson(returnType) || method.getReturnType().getAnnotation(JsonModel.class) != null;
        if (!isReturnTypeJson) {
            throw new IllegalArgumentException("JSON-RPC method must return a JSON instance, invalid type: '" + method.getReturnType() + "'");
        }

        final var packagePrefix = packageName == null || packageName.isBlank() ? "" : (packageName + '.');
        final var packageLine = packagePrefix.isBlank() ? "" : ("package " + packageName + ";\n\n");
        final var endpoint = method.getAnnotation(JsonRpc.class);
        final var priority = findPriority(method);
        final var methodClassName = className + SUFFIX;

        // drop the method name to get the enclosing one
        final var enclosingClassName = className.substring(0, className.lastIndexOf('$')).replace('$', '.');
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
                            .filter(it -> !"context.request()".equals(it.invocation()))
                            .map(it -> {
                                final var meta = jsonRpcParams.get(counter.getAndIncrement());
                                return new PartialOpenRPC.Value(
                                        meta.name(),
                                        null, null, null,
                                        getSchema(meta.type()));
                            })
                            .toList(),
                    new PartialOpenRPC.Value(
                            "result",
                            null, null, null,
                            getSchema(new EnrichedParsedType(returnType))),
                    Stream.of(endpoint.errors())
                            .map(it -> new PartialOpenRPC.ErrorValue(it.code(), it.documentation(), null))
                            .toList()));
        }

        return new BaseHttpEndpointGenerator.Generation(
                new

                        GeneratedClass(packagePrefix + methodClassName, packageLine +
                        generationVersion() +
                        "public class " + methodClassName + " extends " + DefaultJsonRpcMethod.class.

                        getName() + " {\n" +

                        declareConstructor(methodClassName, enclosingClassName, params, true) +
                        "    super(\n" +
                        "      " + priority + ",\n" +
                        "      \"" + endpoint.value().

                        replace("\"", "\\\"").

                        replace("\n", "\\n") + "\",\n" +
                        "      " +

                        invocation(params, returnType) + ");\n" +
                        "  }\n" +
                        "}\n" +
                        "\n"),
                generateBean ?
                        new

                                GeneratedClass(packagePrefix + methodClassName + '$' + FusionBean.class.getSimpleName(), packageLine +

                                generationVersion() +
                                "public class " + methodClassName + '$' + FusionBean.class.

                                getSimpleName() + " extends " + BaseBean.class.

                                getName() + "<" + methodClassName + "> {\n" +
                                "  public " + methodClassName + '$' + FusionBean.class.

                                getSimpleName() + "() {\n" +
                                "    super(\n" +
                                "      " + methodClassName + ".class,\n" +
                                "      " +

                                findScope(method) + ".class,\n" +
                                "      " + priority + ",\n" +
                                "      " + Map.class.

                                getName() + ".of());\n" +
                                "  }\n" +
                                "\n" +
                                "  @Override\n" +
                                "  public " + methodClassName + " create(final " + RuntimeContainer.class.

                                getName() + " container, final " +
                                List.class.

                                        getName() + "<" + Instance.class.

                                getName() + "<?>> dependents) {\n" +

                                createBeanInstance(true, methodClassName, enclosingClassName, params) +
                                "  }\n" +
                                "}\n" +
                                "\n") :
                        null);
    }

    private ParsedType unwrapReturnedType(final TypeMirror typeMirror, final ParsedType type) {
        return type.type() == ParsedType.Type.PARAMETERIZED_TYPE && PartialResponse.class.getName().equals(type.raw()) ?
                ParsedType.of(((DeclaredType) typeMirror).getTypeArguments().get(0)) :
                type;
    }

    // get it from jsonschemas - ideally we should extract the logic from JsonCodecGenerator but for now just use it
    // and assume openrpc spec generation is only enabled if json one is
    private JsonSchema generateFullJsonSchema(final String name) {
        return requireNonNull(allJsonSchemas.get(name),
                "Missing JSON schema for '" + name + "', check you enabled its generation.");
    }

    private JsonSchema getSchema(final EnrichedParsedType enrichedParsedType) {
        final var type = enrichedParsedType.type();
        switch (type.type()) {
            case CLASS -> {
                return switch (type.className()) {
                    case "java.lang.Object" -> new JsonSchema(null, null, "object", true, null, null, true, null, null);
                    case "boolean", "java.lang.Boolean" ->
                            new JsonSchema(null, null, "boolean", !"boolean".equals(type.className()), null, null, null, null, null);
                    case "int", "java.lang.Integer" ->
                            new JsonSchema(null, null, "integer", !"int".equals(type.className()), "int32", null, null, null, null);
                    case "long", "java.lang.Long" ->
                            new JsonSchema(null, null, "integer", !"long".equals(type.className()), "int64", null, null, null, null);
                    case "java.lang.String" -> new JsonSchema(null, null, "string", true, null, null, null, null, null);
                    default -> {
                        if (type.enumValues() != null) {
                            yield new JsonSchema(
                                    null, null,
                                    "string",
                                    null, null, null, null, null, null, null, null,
                                    type.enumValues());
                        }
                        final var schema = openRPC.schemas().computeIfAbsent(
                                type.className().replace('$', '.'),
                                k -> generateFullJsonSchema(type.className()));
                        yield new JsonSchema(
                                "#/schemas/" + requireNonNull(schema.id(), "missing id: " + schema),
                                null, null, null, null, null, null, null, null);
                    }
                };
            }
            case PARAMETERIZED_TYPE -> {
                switch (type.raw()) {
                    case "java.util.Collection", "java.util.List", "java.util.Set" -> {
                        return new JsonSchema(
                                null, null,
                                "array", true,
                                null, null,
                                null, null,
                                switch (type.args().get(0)) {
                                    case "java.lang.Object" ->
                                            new JsonSchema(null, null, "object", true, null, null, true, null, null);
                                    case "boolean", "java.lang.Boolean" ->
                                            new JsonSchema(null, null, "boolean", !"boolean".equals(type.args().get(0)), null, null, null, null, null);
                                    case "int", "java.lang.Integer" ->
                                            new JsonSchema(null, null, "integer", !"int".equals(type.args().get(0)), "int32", null, null, null, null);
                                    case "long", "java.lang.Long" ->
                                            new JsonSchema(null, null, "integer", !"long".equals(type.args().get(0)), "int64", null, null, null, null);
                                    case "java.lang.String" ->
                                            new JsonSchema(null, null, "string", true, null, null, null, null, null);
                                    default -> {
                                        final var schema = openRPC.schemas().computeIfAbsent(
                                                type.args().get(0).replace('$', '.'),
                                                k -> generateFullJsonSchema(type.args().get(0)));
                                        yield new JsonSchema(
                                                "#/schemas/" + requireNonNull(schema.id(), "missing id: " + schema),
                                                null, null, null, null, null, null, null, null);
                                    }
                                });
                    }
                    case "java.util.Map" -> {
                        return new JsonSchema(
                                null, null,
                                "object", true,
                                null, null,
                                (switch (type.args().get(1)) {
                                    case "java.lang.Object" ->
                                            new JsonSchema(null, null, "object", true, null, null, true, null, null);
                                    case "boolean", "java.lang.Boolean" ->
                                            new JsonSchema(null, null, "boolean", !"boolean".equals(type.args().get(1)), null, null, null, null, null);
                                    case "int", "java.lang.Integer" ->
                                            new JsonSchema(null, null, "integer", !"int".equals(type.args().get(1)), "int32", null, null, null, null);
                                    case "long", "java.lang.Long" ->
                                            new JsonSchema(null, null, "integer", !"long".equals(type.args().get(1)), "int64", null, null, null, null);
                                    case "java.lang.String" ->
                                            new JsonSchema(null, null, "string", true, null, null, null, null, null);
                                    default -> {
                                        final var schema = openRPC.schemas().computeIfAbsent(
                                                type.args().get(1).replace('$', '.'),
                                                k -> generateFullJsonSchema(type.args().get(1)));
                                        yield new JsonSchema(
                                                "#/schemas/" + requireNonNull(schema.id(), "missing id: " + schema),
                                                null, null, null, null, null, null, null, null);
                                    }
                                }).asMap(),
                                null, null);
                    }
                    case "java.util.Optional", "java.util.concurrent.CompletionStage" -> {
                        return new JsonSchema(
                                null, null,
                                "object", true,
                                null, null,
                                (switch (type.args().get(0)) {
                                    case "java.lang.Object" ->
                                            new JsonSchema(null, null, "object", true, null, null, true, null, null);
                                    case "boolean", "java.lang.Boolean" ->
                                            new JsonSchema(null, null, "boolean", !"boolean".equals(type.args().get(0)), null, null, null, null, null);
                                    case "int", "java.lang.Integer" ->
                                            new JsonSchema(null, null, "integer", !"int".equals(type.args().get(0)), "int32", null, null, null, null);
                                    case "long", "java.lang.Long" ->
                                            new JsonSchema(null, null, "integer", !"long".equals(type.args().get(0)), "int64", null, null, null, null);
                                    case "java.lang.String" ->
                                            new JsonSchema(null, null, "string", true, null, null, null, null, null);
                                    default -> {
                                        final var schema = openRPC.schemas().computeIfAbsent(
                                                type.args().get(0).replace('$', '.'),
                                                k -> generateFullJsonSchema(type.args().get(0)));
                                        yield new JsonSchema(
                                                "#/schemas/" + requireNonNull(schema.id(), "missing id: " + schema),
                                                null, null, null, null, null, null, null, null);
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

    // final Function<Context, CompletionStage<Object>>
    private String invocation(final List<Param> params,
                              final ParsedType returnType) {
        final var delegation = forceCompletionStageResult(returnType, params.stream()
                .map(it -> it.promiseName() != null ? it.promiseName() : it.invocation())
                .collect(joining(", ", "root." + method.getSimpleName() + "(", ")")));
        return "context -> " + delegation;
    }

    @Override
    protected Param createJsonParam(final VariableElement it, final ParsedType param,
                                    final int index) {
        final var conf = it.getAnnotation(JsonRpcParam.class);
        final var rawName = ofNullable(conf)
                .map(JsonRpcParam::value)
                .filter(name -> !name.isBlank())
                .orElseGet(() -> it.getSimpleName().toString())
                .replace("\"", "\\\"").replace("\n", "\\n");

        final var paramName = "\"" + rawName + "\"";
        jsonRpcParams.add(new ParamMeta(rawName, new EnrichedParsedType(ParsedType.of(it.asType()))));

        return new Param(
                "final " + JsonMapper.class.getName() + " jsonMapper",
                "lookup(container, " + JsonMapper.class.getName() + ".class, dependents)",
                "\n                        findParameter(" +
                        "context, " +
                        paramName + ", " +
                        index + ", " +
                        ofNullable(conf)
                                .map(JsonRpcParam::required)
                                .orElse(false) + ",\n" +
                        "                                value -> " + mapValue(paramName, param) + ")",
                null);
    }

    // keep in mind we use Object codec so we must convert with the related types
    private String mapValue(final String name, final ParsedType param) {
        return switch (param.type()) {
            case CLASS -> switch (param.className()) {
                case "java.lang.Object" -> "value";
                case "boolean" -> Boolean.class.getName() + ".TRUE.equals(value)";
                case "java.lang.Boolean" -> "(java.lang.Boolean) value";
                case "int" -> "value == null ? 0 :((java.lang.Number) value).intValue()";
                case "java.lang.Integer" -> "value == null ? null : ((java.lang.Number) value).intValue()";
                case "long" -> "value == null ? 0L : ((java.lang.Number) value).longValue()";
                case "java.lang.Long" -> "value == null ? null : ((java.lang.Number) value).longValue()";
                case "java.lang.String" -> "(java.lang.String) value";
                default -> // not the most sexy, todo: review if we can avoid a roundtrip
                        "value == null ? null : jsonMapper.fromString(" + param.className() + ".class, jsonMapper.toString(value))";
            };
            case PARAMETERIZED_TYPE -> switch (param.raw()) {
                case "java.util.Collection", "java.util.List", "java.util.Set" -> {
                    final var cast = "" +
                            "value == null ?\n" +
                            "                                        null :\n" +
                            "                                        ((java.util.Collection<?>) " +
                            "failIfNot(" + name + ", value, it -> it instanceof java.util.Collection<?>)).stream()\n                                  .";
                    final var collector = switch (param.raw()) {
                        case "java.util.Collection", "java.util.List" -> "toList()";
                        case "java.util.Set" -> "collect(" + Collectors.class.getName() + ".toSet())";
                        default -> throw new IllegalStateException("impossible");
                    };

                    yield switch (param.args().get(0)) {
                        case "java.lang.Object" -> cast + "map(it -> (Object) it)." + collector;
                        case "boolean" -> cast +
                                "map(item -> " + Boolean.class.getName() + ".TRUE.equals(item))." +
                                collector;
                        case "java.lang.Boolean" -> cast +
                                "map(item -> item == null ? null : " + Boolean.class.getName() + ".TRUE.equals(item))." +
                                collector;
                        case "int" ->
                                cast + "map(item -> item == null ? 0 : ((java.lang.Number) item).intValue())." + collector;
                        case "java.lang.Integer" -> cast +
                                "map(item -> item == null ? null : ((java.lang.Number) item).intValue())." +
                                collector;
                        case "long" ->
                                cast + "map(item -> item == null ? 0L : ((java.lang.Number) item).longValue())." + collector;
                        case "java.lang.Long" -> cast +
                                "map(item -> item == null ? null : ((java.lang.Number) item).longValue())." +
                                collector;
                        case "java.lang.String" -> cast +
                                "map(item -> (java.lang.String) item)." +
                                collector;
                        default -> // not the most sexy, todo: review if we can avoid a roundtrip
                                "value == null ?" +
                                        " null :" +
                                        " jsonMapper.fromString(" + param.createParameterizedTypeImpl() + ", jsonMapper.toString(value))";
                    };
                }
                case "java.util.Map" -> {
                    if (!String.class.getName().equals(param.args().get(0))) {
                        throw new IllegalArgumentException("Only Map<String, X> are supported in root JSON-RPC parameters, got " + param.createParameterizedTypeCast());
                    }

                    final var cast = "" +
                            "value == null ?" +
                            " null :" +
                            " failIfNotMap(" + name + ", value)" +
                            ".entrySet().stream()\n                                        ";
                    final var collector = "\n                                        .collect(" + Collectors.class.getName() + ".toMap(" +
                            Map.class.getName() + ".Entry::getKey, " +
                            Map.class.getName() + ".Entry::getValue))";

                    yield switch (param.args().get(1)) {
                        case "java.lang.Object" -> cast + collector;
                        case "java.lang.Boolean" -> cast +
                                ".map(item -> " + Map.class.getName() + ".entry(item.getKey(), item.getValue() == null ?" +
                                " null : " + Boolean.class.getName() + ".TRUE.equals(item.getValue())))" +
                                collector;
                        case "java.lang.Integer" -> cast +
                                ".map(item -> " + Map.class.getName() + ".entry(item.getKey(), item.getValue() == null ?" +
                                " null : ((java.lang.Number) item.getValue()).intValue()))" +
                                collector;
                        case "java.lang.Long" -> cast +
                                ".map(item -> " + Map.class.getName() + ".entry(item.getKey(), item.getValue() == null ?" +
                                " null : ((java.lang.Number) item.getValue()).longValue()))" +
                                collector;
                        case "java.lang.String" -> cast +
                                ".map(item -> " + Map.class.getName() + ".entry(item.getKey(), (java.lang.String) item.getValue()))" +
                                collector;
                        default -> // not the most sexy, todo: review if we can avoid a roundtrip
                                "value == null ?\n" +
                                        "                                        null :\n" +
                                        "                                        jsonMapper.fromString(" + param.createParameterizedTypeImpl() + ", jsonMapper.toString(value))";
                    };
                }
                case "java.util.Optional" -> switch (param.args().get(0)) {
                    case "java.lang.Object" -> Optional.class.getName() + ".ofNullable(value)";
                    case "java.lang.Boolean" ->
                            Optional.class.getName() + ".ofNullable(value == null ? (" + Boolean.class.getName() + ") null : " + Boolean.class.getName() + ".TRUE.equals(item))";
                    case "java.lang.Integer" ->
                            Optional.class.getName() + ".ofNullable(value == null ? (" + Integer.class.getName() + ") null : ((java.lang.Number) value).intValue())";
                    case "java.lang.Long" ->
                            Optional.class.getName() + ".ofNullable(value == null ? (" + Long.class.getName() + ") null : ((java.lang.Number) value).longValue())";
                    case "java.lang.String" ->
                            Optional.class.getName() + ".ofNullable(value == null ? (" + String.class.getName() + ")null : ((String) value))";
                    default -> // not the most sexy, todo: review if we can avoid a roundtrip
                            "value == null ? null : " +
                                    Optional.class.getName() + ".ofNullable(value == null ? (" + param.args().get(0) + ") null : " +
                                    "jsonMapper.fromString(" + param.args().get(0) + ".class, jsonMapper.toString(value)))";
                };
                default ->
                        throw new IllegalArgumentException("For now generic parameterized type in JSON-RPC methods are not supported, only collections and optionals.");
            };
        };
    }

    @Override
    protected Param createRequestParameter() {
        return new Param(null, null, "context.request()", null);
    }

    private record EnrichedParsedType(ParsedType type) { // potentially get the Element or TypeMirror later
    }

    private record ParamMeta(String name, EnrichedParsedType type) {
    }
}
