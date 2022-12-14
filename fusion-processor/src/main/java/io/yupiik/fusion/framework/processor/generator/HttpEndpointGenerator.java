package io.yupiik.fusion.framework.processor.generator;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.build.api.http.HttpMatcher;
import io.yupiik.fusion.framework.processor.Elements;
import io.yupiik.fusion.framework.processor.ParsedType;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.impl.DefaultEndpoint;
import io.yupiik.fusion.http.server.impl.io.RequestBodyAggregator;
import io.yupiik.fusion.http.server.impl.matcher.CaseInsensitiveValuesMatcher;
import io.yupiik.fusion.http.server.impl.matcher.PatternMatcher;
import io.yupiik.fusion.http.server.impl.matcher.ValueMatcher;
import io.yupiik.fusion.json.JsonMapper;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public class HttpEndpointGenerator extends BaseGenerator implements Supplier<HttpEndpointGenerator.Generation> {
    private static final String SUFFIX = "$FusionHttpEndpoint";

    private final boolean generateBean;
    private final ExecutableElement method;
    private final String packageName;
    private final String className;
    private final Set<String> knownJsonModels;

    public HttpEndpointGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                                 final boolean generateBean, final String packageName, final String className,
                                 final ExecutableElement method, final Set<String> knownJsonModels) {
        super(processingEnv, elements);
        this.generateBean = generateBean;
        this.method = method;
        this.packageName = packageName;
        this.className = className;
        this.knownJsonModels = knownJsonModels;
    }

    @Override
    public Generation get() {
        final var packagePrefix = packageName == null || packageName.isBlank() ? "" : (packageName + '.');
        final var packageLine = packagePrefix.isBlank() ? "" : ("package " + packageName + ";\n\n");
        final var matcher = method.getAnnotation(HttpMatcher.class);
        final var endpointClassName = className + SUFFIX;

        // drop the method name to get the enclosing one
        final var enclosingClassName = className.substring(0, className.lastIndexOf('$')).replace('$', '.');

        final var returnType = ParsedType.of(method.getReturnType());
        final var params = method.getParameters().stream()
                .map(it -> {
                    final var param = ParsedType.of(it.asType());
                    if (isRequest(param)) {
                        return new Param(null, null, "request", null);
                    }
                    if (isJson(param)) {
                        return new Param(
                                "final " + JsonMapper.class.getName() + " jsonMapper",
                                "lookup(container, " + JsonMapper.class.getName() + ".class, dependents)",
                                "new " + RequestBodyAggregator.class.getName() + "(request.body())\n" +
                                        "          .promise()\n" +
                                        "          .thenApply(payload -> jsonMapper.fromString(" +
                                        switch (param.type()) {
                                            case CLASS -> param.className() + ".class";
                                            case PARAMETERIZED_TYPE -> // todo: store it in fields for speed - avoid alloc - or is it rare enough?
                                                    "new " + Types.ParameterizedTypeImpl.class.getName() + "(" +
                                                            param.raw() + ".class, " +
                                                            param.args().stream().map(a -> a + ".class").collect(joining(",")) + ")";
                                        } + ", payload))",
                                "payload");
                    }
                    throw new IllegalArgumentException("Invalid parameter to " + method.getEnclosingElement() + "." + method + ", only Request is supported");
                })
                .toList();

        final boolean isReturnTypeJson = isJson(returnType);

        return new Generation(
                // todo: note that we can add an option to only generate the bean since we could do new DefaultEndpoint and bypass the endpoint class
                new GeneratedClass(packagePrefix + endpointClassName, packageLine +
                        "public class " + endpointClassName + " extends " + DefaultEndpoint.class.getName() + " {\n" +
                        Stream.concat(
                                        Stream.of("final " + enclosingClassName + " root"),
                                        Stream.concat(
                                                        !isReturnTypeJson ?
                                                                Stream.empty() :
                                                                Stream.of("final " + JsonMapper.class.getName() + " jsonMapper"),
                                                        params.stream()
                                                                .map(Param::constructorParam)
                                                                .filter(Objects::nonNull))
                                                .distinct())
                                .collect(joining(
                                        ", ",
                                        "  public " + endpointClassName + "(",
                                        ") {\n")) +
                        "    super(\n" +
                        "      " + matcher.priority() + ",\n" +
                        "      " + matcherOf(matcher) + ",\n" +
                        "      " + handler(params, returnType, isReturnTypeJson) + ");\n" +
                        "  }\n" +
                        "}\n" +
                        "\n"),
                generateBean ?
                        new GeneratedClass(packagePrefix + endpointClassName + '$' + FusionBean.class.getSimpleName(), packageLine +
                                "public class " + endpointClassName + '$' + FusionBean.class.getSimpleName() + " extends " + BaseBean.class.getName() + "<" + endpointClassName + "> {\n" +
                                "  public " + endpointClassName + '$' + FusionBean.class.getSimpleName() + "() {\n" +
                                "    super(\n" +
                                "      " + endpointClassName + ".class,\n" +
                                "      " + findScope(method) + ".class,\n" +
                                "      " + findPriority(method) + ",\n" +
                                "      " + Map.class.getName() + ".of());\n" +
                                "  }\n" +
                                "\n" +
                                "  @Override\n" +
                                "  public " + endpointClassName + " create(final " + RuntimeContainer.class.getName() + " container, final " +
                                List.class.getName() + "<" + Instance.class.getName() + "<?>> dependents) {\n" +
                                Stream.concat(
                                                Stream.of("lookup(container, " + enclosingClassName + ".class, dependents)"),
                                                Stream.concat(
                                                                !isReturnTypeJson ?
                                                                        Stream.empty() :
                                                                        Stream.of("lookup(container, " + JsonMapper.class.getName() + ".class, dependents)"),
                                                                params.stream()
                                                                        .map(Param::lookup)
                                                                        .filter(Objects::nonNull))
                                                        .distinct())
                                        .collect(joining(
                                                ", ",
                                                "    return new " + endpointClassName + "(",
                                                ");\n")) +
                                "  }\n" +
                                "}\n" +
                                "\n") :
                        null);
    }

    private String handler(final List<Param> params, final ParsedType returnType, final boolean returnJson) {
        final var asyncParams = params.stream().filter(p -> p.promiseName() != null).toList();
        final var out = new StringBuilder();
        int indent = 0;
        for (final var param : asyncParams) {
            out.append((param.invocation() + "\n          .thenCompose(" + param.promiseName() + " ->\n").indent(indent));
            indent += 2;
        }

        final var call = "root." + method.getSimpleName() + "(" +
                params.stream()
                        .map(it -> it.promiseName() != null ? it.promiseName() : it.invocation())
                        .collect(joining(", ")) +
                ")";
        final var result = (switch (returnType.type()) {
            case PARAMETERIZED_TYPE -> {
                if (CompletionStage.class.getName().equals(returnType.raw()) || CompletableFuture.class.getName().equals(returnType.raw())) {
                    yield call;
                }
                // unlikely for now but once we open returned types it will be possible
                yield CompletableFuture.class.getName() + ".completedStage(" + call + ")";
            }
            case CLASS -> CompletableFuture.class.getName() + ".completedStage(" + call + ")";
        }).indent(indent + "          ".length()).stripTrailing();
        out.append(result);

        if (!asyncParams.isEmpty()) {
            out.append(")".repeat(asyncParams.size()));
        }

        return "request -> " + out +
                (!returnJson ? "" : "\n" +
                        "          .thenApply(jsonResult -> " + Response.class.getName() + ".of()\n" +
                        "            .status(200)\n" +
                        "            .header(\"content-type\", \"application/json\")\n" +
                        "            .body(jsonMapper.toString(jsonResult))\n" +
                        "            .build())");
    }

    private String matcherOf(final HttpMatcher matcher) {
        final var matchers = Stream.of(
                        // optimised method matcher
                        switch (matcher.methods().length) {
                            case 0 -> null;
                            case 1 -> "new " + ValueMatcher.class.getName() + "<>(" +
                                    Request.class.getName() + "::method, \"" + matcher.methods()[0] + "\", String::equalsIgnoreCase)";
                            default -> "new " + CaseInsensitiveValuesMatcher.class.getName() + "<>(" +
                                    Request.class.getName() + "::method, " +
                                    Stream.of(matcher.methods()).map(it -> "\"" + it + "\"").collect(joining(", ")) + ")";
                        },
                        // optimised path matcher
                        switch (matcher.pathMatching()) {
                            case IGNORED -> null;
                            case EXACT -> "new " + ValueMatcher.class.getName() + "<>(" +
                                    Request.class.getName() + "::path, \"" + matcher.path() + "\")";
                            case REGEX -> "new " + PatternMatcher.class.getName() + "<>(" +
                                    Request.class.getName() + "::path,  \"" + matcher.path() + "\", (req, matcher) -> req.setAttribute(\"fusion.http.matcher\", matcher))";
                            case STARTS_WITH -> "new " + ValueMatcher.class.getName() + "<>(" +
                                    Request.class.getName() + "::path, \"" + matcher.path() + "\", String::startsWith)";
                            case ENDS_WITH -> "new " + ValueMatcher.class.getName() + "<>(" +
                                    Request.class.getName() + "::path, \"" + matcher.path() + "\", String::endsWith)";
                        })
                .filter(Objects::nonNull)
                .toList();
        return matchers.isEmpty() ?
                "request -> true" :
                matchers.stream().collect(joining(")\n          .and(", "(", ")"));
    }

    private boolean isRequest(final ParsedType param) {
        return param.type() == ParsedType.Type.CLASS && Request.class.getName().equals(param.className());
    }

    private boolean isJson(final ParsedType param) {
        return switch (param.type()) {
            case CLASS -> knownJsonModels.contains(param.className());
            case PARAMETERIZED_TYPE ->
                    knownJsonModels.contains(param.args().get(0)); // todo: test raw = list or set or optional?
        };
    }

    public record Generation(GeneratedClass endpoint, GeneratedClass bean) {
    }

    public record Param(String constructorParam, String lookup,
                        String invocation, String promiseName) {
    }
}
