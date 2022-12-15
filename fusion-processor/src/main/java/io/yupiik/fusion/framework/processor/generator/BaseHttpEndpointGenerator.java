package io.yupiik.fusion.framework.processor.generator;

import io.yupiik.fusion.framework.processor.Elements;
import io.yupiik.fusion.framework.processor.ParsedType;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.impl.io.RequestBodyAggregator;
import io.yupiik.fusion.json.JsonMapper;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public abstract class BaseHttpEndpointGenerator extends BaseGenerator {
    protected final boolean generateBean;
    protected final ExecutableElement method;
    protected final String packageName;
    protected final String className;
    protected final Set<String> knownJsonModels;

    public BaseHttpEndpointGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                                     final boolean generateBean, final String packageName, final String className,
                                     final ExecutableElement method, final Set<String> knownJsonModels) {
        super(processingEnv, elements);
        this.generateBean = generateBean;
        this.method = method;
        this.packageName = packageName;
        this.className = className;
        this.knownJsonModels = knownJsonModels;
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
            case CLASS -> CompletableFuture.class.getName() + ".completedStage(" + call + ")";
        });
    }

    protected String createBeanInstance(final boolean isReturnTypeJson, final String methodClassName,
                                        final String enclosingClassName, final List<Param> params) {
        return Stream.concat(
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
                        "    return new " + methodClassName + "(",
                        ");\n"));
    }

    protected String declareConstructor(final String endpointClassName, final String enclosingClassName,
                                        final List<Param> params, final boolean isReturnTypeJson) {
        return Stream.concat(
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
                        ") {\n"));
    }

    protected List<Param> prepareParams() {
        final var index = new AtomicInteger();
        return method.getParameters().stream()
                .map(it -> {
                    final var param = ParsedType.of(it.asType());
                    if (isRequest(param)) {
                        return createRequestParameter();
                    }
                    if (isJson(param)) {
                        return createJsonParam(it, param, index.getAndIncrement());
                    }
                    throw new IllegalArgumentException("Invalid parameter to " + method.getEnclosingElement() + "." + method + ", only Request is supported");
                })
                .toList();
    }

    protected Param createJsonParam(final VariableElement it, final ParsedType param, final int index) {
        return new Param(
                "final " + JsonMapper.class.getName() + " jsonMapper",
                "lookup(container, " + JsonMapper.class.getName() + ".class, dependents)",
                "new " + RequestBodyAggregator.class.getName() + "(request.body())\n" +
                        "          .promise()\n" +
                        "          .thenApply(payload -> jsonMapper.fromString(" +
                        switch (param.type()) {
                            case CLASS -> param.className() + ".class";
                            case PARAMETERIZED_TYPE -> // todo: store it in fields for speed - avoid alloc - or is it rare enough?
                                    param.createParameterizedTypeImpl();
                        } + ", payload))",
                "payload");
    }

    protected Param createRequestParameter() {
        return new Param(null, null, "request", null);
    }

    protected boolean isRequest(final ParsedType param) {
        return param.type() == ParsedType.Type.CLASS && Request.class.getName().equals(param.className());
    }

    protected boolean isJson(final ParsedType param) {
        return switch (param.type()) {
            case CLASS -> knownJsonModels.contains(param.className()) ||
                    int.class.getName().equals(param.className()) ||
                    long.class.getName().equals(param.className()) ||
                    boolean.class.getName().equals(param.className()) ||
                    String.class.getName().equals(param.className()) ||
                    Integer.class.getName().equals(param.className()) ||
                    Long.class.getName().equals(param.className()) ||
                    Boolean.class.getName().equals(param.className()) ||
                    Object.class.getName().equals(param.className());
            case PARAMETERIZED_TYPE -> // todo: test raw = collection or list or set or map or optional?
                    isJson(new ParsedType(ParsedType.Type.CLASS, param.args().get(param.args().size() - 1), null, null));
        };
    }

    public record Generation(BaseGenerator.GeneratedClass endpoint, BaseGenerator.GeneratedClass bean) {
    }

    public record Param(String constructorParam, String lookup,
                        String invocation, String promiseName) {
    }
}
