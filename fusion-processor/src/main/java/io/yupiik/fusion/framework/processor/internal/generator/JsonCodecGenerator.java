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

import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.json.JsonIgnore;
import io.yupiik.fusion.framework.build.api.json.JsonOthers;
import io.yupiik.fusion.framework.build.api.json.JsonProperty;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.ParsedType;
import io.yupiik.fusion.framework.processor.internal.meta.JsonSchema;
import io.yupiik.fusion.framework.processor.internal.metadata.MetadataContributorRegistry;
import io.yupiik.fusion.json.serialization.JsonCodec;
import io.yupiik.fusion.json.serialization.MetadataJsonCodec;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static javax.lang.model.element.ElementKind.RECORD;

public class JsonCodecGenerator extends BaseGenerator implements Supplier<BaseGenerator.GeneratedClass> {
    // Persistent identity only, no class with this name is emitted.
    public static final String SUFFIX = "$FusionJsonCodec";
    private static final String RUNTIME = MetadataJsonCodec.class.getName();
    private static final String LIST_PREFIX = List.class.getName() + "<";
    private static final String COLLECTION_PREFIX = Collection.class.getName() + "<";
    private static final String SET_PREFIX = Set.class.getName() + "<";
    private static final String MAP_STRING_PREFIX = Map.class.getName() + "<" + String.class.getName() + ",";

    private final String packageName;
    private final String className;
    private final TypeElement element;
    private final Collection<String> models;
    private final Map<String, JsonSchema> jsonSchemas;
    private final boolean generateBean;

    public JsonCodecGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                              final MetadataContributorRegistry metadataContributorRegistry,
                              final String packageName, final String className, final TypeElement element,
                              final Collection<String> models, final Map<String, JsonSchema> jsonSchemasCollector,
                              final boolean generateBean) {
        super(processingEnv, elements, metadataContributorRegistry);
        this.packageName = packageName;
        this.className = className;
        this.element = element;
        this.models = models;
        this.jsonSchemas = jsonSchemasCollector;
        this.generateBean = generateBean;
    }

    @Override
    public GeneratedClass get() {
        final var packagePrefix = packageName.isBlank() ? "" : packageName + '.';
        final var modelClass = element.asType().toString();
        final var params = selectConstructor(element)
                .map(constructor -> constructor.getParameters().stream().map(it -> {
                    final var javaName = it.getSimpleName().toString();
                    final var type = it.asType();
                    return new Param(javaName,
                            ofNullable(it.getAnnotation(JsonProperty.class)).map(JsonProperty::value)
                                    .filter(Predicate.not(String::isBlank)).orElse(javaName),
                            type, typeOf(type.toString(), type),
                            it.getAnnotation(JsonOthers.class) != null, it.getAnnotation(JsonIgnore.class) != null,
                            ofNullable(it.getAnnotation(Property.class))
                                    .map(i -> i.documentation() + (i.required() ? " This attribute is required." : "")).orElse(null),
                            ofNullable(it.getAnnotation(JsonProperty.class)).map(JsonProperty::order).orElse(Integer.MIN_VALUE));
                }).toList()).orElse(List.of());
        for (final var param : params) {
            if (param.others() && !(param.types().paramType() == ParamType.MAP &&
                    param.types().paramTypeDef() == ParamTypeDef.GENERIC_OBJECT &&
                    Object.class.getName().equals(param.types().argTypeIfNotValue().toString()))) {
                throw new IllegalArgumentException("Unsupported attribute: '" + param.javaName() + "' in '" + modelClass +
                        "', should be Map<String, Object> due to @JsonOthers annotation.");
            }
        }
        if (jsonSchemas != null) {
            final var fqn = element.getQualifiedName().toString();
            jsonSchemas.put(fqn, new JsonSchema(null, fqn, "object", null, null, null, null,
                    params.stream().collect(toMap(Param::jsonName, Param::schema)), null,
                    fqn.substring(Math.max(fqn.lastIndexOf('$'), fqn.lastIndexOf('.')) + 1), null, null));
        }
        final var id = packagePrefix + className + SUFFIX;
        final var artifacts = new ArrayList<Binding.Artifact>();
        artifacts.add(new Binding.Artifact(id, codecBinding(modelClass, params)));
        if (generateBean) {
            final var beanId = id + "." + FusionBean.class.getSimpleName();
            artifacts.add(new Binding.Artifact(beanId, codecBean(packageName, modelClass, id)));
        }
        return new GeneratedClass(id, null, artifacts);
    }

    private Binding codecBinding(final String modelClass, final List<Param> params) {
        if (params.stream().filter(Param::others).count() > 1) {
            throw new IllegalArgumentException("You can only get a single @JsonOthers per @JsonModel record");
        }
        if (params.stream().anyMatch(p -> p.ignored() && p.others())) {
            throw new IllegalArgumentException("A @JsonModel attribute cannot be both @JsonIgnore and @JsonOthers");
        }
        // Three independent orders: constructor slots, parser key lengths, and explicit serialization order.
        final var named = params.stream().filter(p -> !p.others() && !p.ignored())
                .sorted(Comparator.comparingInt(p -> p.jsonName().length())).toList();
        final var read = new ArrayList<>(named);
        params.stream().filter(p -> p.others() || p.ignored()).forEach(read::add);
        final var write = params.stream().filter(p -> !p.ignored())
                .sorted(Comparator.<Param, Integer>comparing(p -> p.order() != Integer.MIN_VALUE ? p.order() :
                                (p.others() ? Integer.MIN_VALUE + 2 : Integer.MIN_VALUE + 1))
                        .thenComparing(Param::javaName)).toList();
        final var properties = read.stream().map(p -> {
            final var slot = params.indexOf(p);
            return "new " + RUNTIME + ".Property(" + slot + ", " + literal(p.jsonName()) + ", " + slot + ", " +
                    RUNTIME + ".ContainerKind." + p.types().paramType().name() + ", " +
                    RUNTIME + ".ValueKind." + p.types().paramTypeDef().name() + ", " + isJavaLangWrapper(p.type()) + ", " +
                    p.others() + ", " + p.ignored() + ", " + delegateTypeExpr(p) + ")";
        }).collect(joining(",\n"));
        final var factory = "new " + RUNTIME + "<" + modelClass + ">(" + modelClass + ".class, java.util.List.of(" +
                properties + "), new int[]{" +
                write.stream().map(p -> Integer.toString(params.indexOf(p))).collect(joining(", ")) +
                "}, java.util.Arrays.<java.util.function.Function<" + modelClass + ", Object>>asList(" +
                params.stream().map(p -> p.ignored() ? "(java.util.function.Function<" + modelClass + ", Object>) null" :
                        modelClass + "::" + p.javaName()).collect(joining(", ")) +
                "), " + Binding.helperReference(12) + ", " + Binding.helperReference(10) + ")";
        final var methods = new ArrayList<Binding.Method>();
        methods.add(new Binding.Method(10,
                (params.stream().anyMatch(p -> p.type().toString().contains("<")) ? "@SuppressWarnings(\"unchecked\")\n" : "") +
                        "private static " + modelClass + " $method(Object[] args) { return new " + modelClass + "(" +
                        params.stream().map(p -> (Object.class.getName().equals(p.type().toString()) ? "" : "(" + p.type() + ") ") +
                                "args[" + params.indexOf(p) + "]").collect(joining(", ")) + "); }", ""));
        final var offsets = new StringBuilder("private static int $method(int length) { return switch (length) {\n");
        for (int i = 0; i < named.size(); i++) {
            if (i == 0 || named.get(i).jsonName().length() != named.get(i - 1).jsonName().length()) {
                offsets.append("case ").append(named.get(i).jsonName().length()).append(" -> ").append(i).append(";\n");
            }
        }
        offsets.append("default -> -1; }; }");
        methods.add(new Binding.Method(12, offsets.toString(), ""));
        return new Binding(packageName, null, JsonCodec.class.getName() + "<" + modelClass + ">", factory, methods);
    }

    // Shared with enum codecs; the registration type is JsonCodec<Model>, never the runtime adapter class.
    public static Binding codecBean(final String packageName, final String modelClass, final String codecId) {
        final var codec = JsonCodec.class.getName() + "<" + modelClass + ">";
        return new Binding(packageName, "bean", FusionBean.class.getName() + "<?>",
                "new io.yupiik.fusion.framework.api.container.bean.FunctionalBean<>(new " +
                        ParsedType.PARAMETERIZED_TYPE_IMPL + "(" + JsonCodec.class.getName() + ".class, " + modelClass + ".class), " +
                        DefaultScoped.class.getName() + ".class, 1000, 0, java.util.Map.of(), " + Binding.helperReference(0) +
                        ", null, null, null, false)",
                List.of(Binding.Method.callback(0, "@SuppressWarnings(\"cast\")\nprivate static " + codec + " $method(" +
                        "io.yupiik.fusion.framework.api.RuntimeContainer container, " +
                        "java.util.List<io.yupiik.fusion.framework.api.Instance<?>> dependents) { return " +
                        Binding.reference(codecId) + "; }", "(container, dependents) -> " + Binding.reference(codecId))));
    }

    private static String literal(final String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                .replace("\r", "\\r").replace("\t", "\\t").replace("\b", "\\b").replace("\f", "\\f") + '"';
    }

    private static boolean isJavaLangWrapper(final TypeMirror type) {
        return type instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te &&
                te.getQualifiedName().toString().startsWith("java.lang.");
    }

    private String delegateTypeExpr(final Param param) {
        if (param.types().paramType() != ParamType.VALUE && param.types().argTypeIfNotValue() != null) {
            final var type = param.types().argTypeIfNotValue().toString();
            final var generics = type.indexOf('<');
            return (generics < 0 ? type : type.substring(0, generics)) + ".class";
        }
        return switch (param.types().paramTypeDef()) {
            case STRING, INTEGER, LONG, DOUBLE, BOOLEAN -> "null";
            case ENUM, MODEL -> param.type() + ".class";
            case BIG_DECIMAL -> BigDecimal.class.getName() + ".class";
            case NUMBER -> Number.class.getName() + ".class";
            case LOCAL_DATE -> LocalDate.class.getName() + ".class";
            case LOCAL_DATE_TIME -> LocalDateTime.class.getName() + ".class";
            case OFFSET_DATE_TIME -> OffsetDateTime.class.getName() + ".class";
            case ZONED_DATE_TIME -> ZonedDateTime.class.getName() + ".class";
            case GENERIC_OBJECT -> Object.class.getName() + ".class";
        };
    }

    private ParamTypes typeOf(final String typeString, final TypeMirror raw) {
        if ((typeString.startsWith(LIST_PREFIX) || typeString.startsWith(COLLECTION_PREFIX) || typeString.startsWith(SET_PREFIX)) && typeString.endsWith(">")) {
            final var arg = ((DeclaredType) raw).getTypeArguments().get(0);
            return new ParamTypes(typeString.startsWith(SET_PREFIX) ? ParamType.SET : ParamType.LIST,
                    ParamTypeDef.of(arg.toString(), processingEnv.getTypeUtils().asElement(arg), models), arg);
        }
        if (typeString.startsWith(MAP_STRING_PREFIX) && typeString.endsWith(">")) {
            final var arg = ((DeclaredType) raw).getTypeArguments().get(1);
            final var nestedType = typeString.substring(MAP_STRING_PREFIX.length(), typeString.length() - 1).strip();
            if (nestedType.contains("<")) {
                final var nested = typeOf(nestedType, arg);
                if (nested.paramType() == ParamType.LIST) {
                    return new ParamTypes(ParamType.MAP_LIST, nested.paramTypeDef(), nested.argTypeIfNotValue());
                }
            }
            return new ParamTypes(ParamType.MAP, ParamTypeDef.of(nestedType, processingEnv.getTypeUtils().asElement(arg), models), arg);
        }
        return new ParamTypes(ParamType.VALUE, ParamTypeDef.of(typeString, processingEnv.getTypeUtils().asElement(raw), models), null);
    }

    private record Param(String javaName, String jsonName, TypeMirror type,
                         ParamTypes types, boolean others, boolean ignored, String doc, int order) {
        public JsonSchema schema() {
            return switch (types.paramType()) {
                case VALUE -> valueSchema();
                case MAP -> new JsonSchema(null, null, "object", true, null, null, valueSchema().asMap(), null, null, null, doc, null);
                case LIST, SET -> new JsonSchema(null, null, "array", null, null, null, null, null, valueSchema(), null, doc, null);
                case MAP_LIST -> new JsonSchema(null, null, "object", true, null, null,
                        new JsonSchema(null, null, "array", null, null, null, null, null, valueSchema()).asMap(), null, null, null, doc, null);
            };
        }

        private JsonSchema valueSchema() {
            final var testedType = ofNullable(types.argTypeIfNotValue()).orElse(type()).toString();
            return switch (types.paramTypeDef()) {
                case BOOLEAN -> new JsonSchema(null, null, "boolean", !"boolean".equals(testedType), null, null, null, null, null, null, doc, null);
                case INTEGER -> new JsonSchema(null, null, "integer", !"int".equals(testedType), "int32", null, null, null, null, null, doc, null);
                case LONG -> new JsonSchema(null, null, "integer", !"long".equals(testedType), "int64", null, null, null, null, null, doc, null);
                case DOUBLE -> new JsonSchema(null, null, "number", !"double".equals(testedType), "double", null, null, null, null, null, doc, null);
                case NUMBER -> new JsonSchema(null, null, "number", true, null, null, null, null, null, null, doc, null);
                case BIG_DECIMAL, STRING -> new JsonSchema(null, null, "string", true, null, null, null, null, null, null, doc, null);
                case ENUM -> new JsonSchema(null, null, "string", true, null, null, null, null, null, null, doc,
                        type == null ? null : ParsedType.of(type).enumValues());
                case LOCAL_DATE -> new JsonSchema(null, null, "string", true, "date", null, null, null, null, null, doc, null);
                case LOCAL_DATE_TIME -> new JsonSchema(null, null, "string", true, null, "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\.[0-9]*)?$", null, null, null, null, doc, null);
                case OFFSET_DATE_TIME -> new JsonSchema(null, null, "string", true, null, "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?([+-]?[0-9]{2}:[0-9]{2})?Z?$", null, null, null, null, doc, null);
                case ZONED_DATE_TIME -> new JsonSchema(null, null, "string", true, null, "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\.[0-9]*)?([+-]?[0-9]{2}:[0-9]{2})?Z?(\\[.*\\])?$", null, null, null, null, doc, null);
                case MODEL -> new JsonSchema("#/schemas/" + testedType, null, null, true, null, null, null, null, null, null, doc, null);
                case GENERIC_OBJECT -> new JsonSchema(null, null, "object", true, null, null, true, null, null, null, doc, null);
            };
        }
    }

    private record ParamTypes(ParamType paramType, ParamTypeDef paramTypeDef, TypeMirror argTypeIfNotValue) {}

    private enum ParamType { VALUE, LIST, SET, MAP, MAP_LIST }

    private enum ParamTypeDef {
        BOOLEAN, BIG_DECIMAL, NUMBER, INTEGER, LONG, DOUBLE, STRING, ENUM,
        LOCAL_DATE, LOCAL_DATE_TIME, OFFSET_DATE_TIME, ZONED_DATE_TIME, GENERIC_OBJECT, MODEL;

        public static ParamTypeDef of(final String name, final Element type, final Collection<String> models) {
            return switch (name) {
                case "boolean", "java.lang.Boolean" -> BOOLEAN;
                case "java.math.BigDecimal" -> BIG_DECIMAL;
                case "java.lang.Number" -> NUMBER;
                case "int", "java.lang.Integer" -> INTEGER;
                case "long", "java.lang.Long" -> LONG;
                case "double", "java.lang.Double" -> DOUBLE;
                case "java.lang.String", "java.lang.CharSequence" -> STRING;
                case "java.time.LocalDate" -> LOCAL_DATE;
                case "java.time.LocalDateTime" -> LOCAL_DATE_TIME;
                case "java.time.OffsetDateTime" -> OFFSET_DATE_TIME;
                case "java.time.ZonedDateTime" -> ZONED_DATE_TIME;
                case "java.lang.Object", "java.util.Map<java.lang.String,java.lang.Object>" -> GENERIC_OBJECT;
                default -> {
                    if (type != null && type.getKind() == RECORD &&
                            models.contains(((TypeElement) type).getQualifiedName().toString())) {
                        yield MODEL;
                    }
                    if (type != null && type.getKind() == ElementKind.ENUM) {
                        yield ENUM;
                    }
                    throw new IllegalArgumentException("Unsupported type: '" + name + "', known models: " + models);
                }
            };
        }
    }
}
