package io.yupiik.fusion.framework.processor.generator;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.http.HttpMatcher;
import io.yupiik.fusion.framework.processor.Elements;
import io.yupiik.fusion.framework.processor.ParsedType;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.impl.DefaultEndpoint;
import io.yupiik.fusion.http.server.impl.matcher.CaseInsensitiveValuesMatcher;
import io.yupiik.fusion.http.server.impl.matcher.PatternMatcher;
import io.yupiik.fusion.http.server.impl.matcher.ValueMatcher;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    public HttpEndpointGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                                 final boolean generateBean, final String packageName, final String className,
                                 final ExecutableElement method) {
        super(processingEnv, elements);
        this.generateBean = generateBean;
        this.method = method;
        this.packageName = packageName;
        this.className = className;
    }

    @Override
    public Generation get() {
        final var packagePrefix = packageName == null || packageName.isBlank() ? "" : (packageName + '.');
        final var packageLine = packagePrefix.isBlank() ? "" : ("package " + packageName + ";\n\n");
        final var matcher = method.getAnnotation(HttpMatcher.class);
        final var endpointClassName = className + SUFFIX;

        // drop the method name to get the enclosing one
        final var enclosingClassName = className.substring(0, className.lastIndexOf('$')).replace('$', '.');

        return new Generation(
                // todo: note that we can add an option to only generate the bean since we could do new DefaultEndpoint and bypass the endpoint class
                new GeneratedClass(packagePrefix + endpointClassName, packageLine +
                        "public class " + endpointClassName + " extends " + DefaultEndpoint.class.getName() + " {\n" +
                        "  public " + endpointClassName + "(final " + enclosingClassName + " root) {\n" +
                        "    super(" +
                        matcher.priority() + ", " +
                        matcherOf(matcher) + ", " +
                        handler() + ");\n" +
                        "  }\n" +
                        "}\n" +
                        "\n"),
                generateBean ?
                        new GeneratedClass(packagePrefix + endpointClassName + '$' + FusionBean.class.getSimpleName(), packageLine +
                                "public class " + endpointClassName + '$' + FusionBean.class.getSimpleName() + " extends " + BaseBean.class.getName() + "<" + endpointClassName + "> {\n" +
                                "  public " + endpointClassName + '$' + FusionBean.class.getSimpleName() + "() {\n" +
                                "    super(" +
                                endpointClassName + ".class, " +
                                // todo: read scope from the method?
                                ApplicationScoped.class.getName() + ".class, " +
                                // assume bean priority == endpoint priority - not always the case but avoids 2 configs for now
                                matcher.priority() + ", " +
                                Map.class.getName() + ".of());\n" +
                                "  }\n" +
                                "\n" +
                                "  @Override\n" +
                                "  public " + endpointClassName + " create(final " + RuntimeContainer.class.getName() + " container, final " +
                                List.class.getName() + "<" + Instance.class.getName() + "<?>> dependents) {\n" +
                                "    return new " + endpointClassName + "(lookup(container, " + enclosingClassName + ".class, dependents));\n" +
                                "  }\n" +
                                "}\n" +
                                "\n") :
                        null);
    }

    private String handler() {
        // todo: support json inputs directly? how to enable that, param on the method? by default? if we add a header matcher?
        final var returnType = ParsedType.of(method.getReturnType());
        final var call = "root." + method.getSimpleName() + "(" +
                method.getParameters().stream()
                        .peek(it -> {
                            final var param = ParsedType.of(it.asType());
                            if (param.type() != ParsedType.Type.CLASS || !Request.class.getName().equals(param.className())) {
                                throw new IllegalArgumentException("Invalid parameter to " + method.getEnclosingElement() + "." + method + ", only Request is supported");
                            }
                        })
                        .map(it -> "request")
                        .collect(joining(", ")) +
                ")";
        return "request -> " + switch (returnType.type()) {
            case PARAMETERIZED_TYPE -> {
                if (CompletionStage.class.getName().equals(returnType.raw()) || CompletableFuture.class.getName().equals(returnType.raw())) {
                    yield call;
                }
                // unlikely for now but once we open returned types it will be possible
                yield CompletableFuture.class.getName() + ".completedStage(" + call + ")";
            }
            case CLASS -> CompletableFuture.class.getName() + ".completedStage(" + call + ")";
        };
    }

    private String matcherOf(final HttpMatcher matcher) {
        final var matchers = Stream.of(
                        // optimised method matcher
                        switch (matcher.methods().length) {
                            case 0 -> null;
                            case 1 -> "new " + ValueMatcher.class.getName() + "<>(" +
                                    Request.class.getName() + "::method, \"" + matcher.methods()[0] + "\")";
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
                matchers.stream().collect(joining(").and(", "(", ")"));
    }

    public record Generation(GeneratedClass endpoint, GeneratedClass bean) {
    }
}
