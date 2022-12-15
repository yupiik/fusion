package io.yupiik.fusion.framework.processor.generator;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.build.api.jsonrpc.JsonRpc;
import io.yupiik.fusion.framework.build.api.jsonrpc.JsonRpcParam;
import io.yupiik.fusion.framework.processor.Elements;
import io.yupiik.fusion.framework.processor.ParsedType;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.jsonrpc.impl.DefaultJsonRpcMethod;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

public class JsonRpcEndpointGenerator extends BaseHttpEndpointGenerator implements Supplier<BaseHttpEndpointGenerator.Generation> {
    private static final String SUFFIX = "$FusionJsonRpcMethod";

    public JsonRpcEndpointGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                                    final boolean beanForJsonRpcEndpoints, final String packageName, final String className,
                                    final ExecutableElement method, final Set<String> knownJsonModels) {
        super(processingEnv, elements, beanForJsonRpcEndpoints, packageName, className, method, knownJsonModels);
    }

    @Override
    public BaseHttpEndpointGenerator.Generation get() {
        final var returnType = ParsedType.of(method.getReturnType());
        final boolean isReturnTypeJson = isJson(returnType);
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

        return new BaseHttpEndpointGenerator.Generation(
                // todo: note that we can add an option to only generate the bean since we could do new DefaultEndpoint and bypass the endpoint class
                new GeneratedClass(packagePrefix + methodClassName, packageLine +
                        generationVersion() +
                        "public class " + methodClassName + " extends " + DefaultJsonRpcMethod.class.getName() + " {\n" +
                        declareConstructor(methodClassName, enclosingClassName, params, isReturnTypeJson) +
                        "    super(\n" +
                        "      " + priority + ",\n" +
                        "      \"" + endpoint.value().replace("\"", "\\\"").replace("\n", "\\n") + "\",\n" +
                        "      " + invocation(params, returnType) + ");\n" +
                        "  }\n" +
                        "}\n" +
                        "\n"),
                generateBean ?
                        new GeneratedClass(packagePrefix + methodClassName + '$' + FusionBean.class.getSimpleName(), packageLine +
                                generationVersion() +
                                "public class " + methodClassName + '$' + FusionBean.class.getSimpleName() + " extends " + BaseBean.class.getName() + "<" + methodClassName + "> {\n" +
                                "  public " + methodClassName + '$' + FusionBean.class.getSimpleName() + "() {\n" +
                                "    super(\n" +
                                "      " + methodClassName + ".class,\n" +
                                "      " + findScope(method) + ".class,\n" +
                                "      " + priority + ",\n" +
                                "      " + Map.class.getName() + ".of());\n" +
                                "  }\n" +
                                "\n" +
                                "  @Override\n" +
                                "  public " + methodClassName + " create(final " + RuntimeContainer.class.getName() + " container, final " +
                                List.class.getName() + "<" + Instance.class.getName() + "<?>> dependents) {\n" +
                                createBeanInstance(true, methodClassName, enclosingClassName, params) +
                                "  }\n" +
                                "}\n" +
                                "\n") :
                        null);
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
        final var paramName = "\"" + ofNullable(conf)
                .map(JsonRpcParam::value)
                .filter(name -> !name.isBlank())
                .orElseGet(() -> it.getSimpleName().toString())
                .replace("\"", "\\\"").replace("\n", "\\n") + "\"";
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
}
