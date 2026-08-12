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

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.fusion.framework.build.api.json.JsonOthers;
import io.yupiik.fusion.framework.build.api.json.JsonProperty;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.ParsedType;
import io.yupiik.fusion.framework.processor.internal.meta.JsonSchema;
import io.yupiik.fusion.framework.processor.internal.metadata.MetadataContributorRegistry;
import io.yupiik.fusion.json.internal.codec.BaseJsonCodec;
import io.yupiik.fusion.json.serialization.JsonCodec;
import io.yupiik.fusion.json.spi.Parser;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static javax.lang.model.element.ElementKind.RECORD;

public class JsonCodecGenerator extends BaseGenerator implements Supplier<BaseGenerator.GeneratedClass> {
    public static final String SUFFIX = "$FusionJsonCodec";

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
        final var packagePrefix = !packageName.isBlank() ? packageName + '.' : "";
        final var modelClass = element.asType().toString();
        final var params = selectConstructor(element)
                .map(constructor -> constructor.getParameters().stream()
                        .map(it -> {
                            final var javaName = it.getSimpleName().toString();
                            final var typeMirror = it.asType();
                            return new Param(
                                    it.getSimpleName().toString(),
                                    ofNullable(it.getAnnotation(JsonProperty.class))
                                            .map(JsonProperty::value)
                                            .filter(Predicate.not(String::isBlank))
                                            .orElse(javaName),
                                    typeMirror,
                                    typeOf(typeMirror.toString(), typeMirror),
                                    it.getAnnotation(JsonOthers.class) != null,
                                    ofNullable(it.getAnnotation(Property.class))
                                            .map(i -> i.documentation() + (i.required() ? " This attribute is required." : ""))
                                            .orElse(null),
                                    ofNullable(it.getAnnotation(JsonProperty.class))
                                            .map(JsonProperty::order)
                                            .orElse(Integer.MIN_VALUE));
                        })
                        .peek(a -> {
                            if (a.others() && !(
                                    a.types().paramType() == ParamType.MAP &&
                                            a.types().paramTypeDef() == ParamTypeDef.GENERIC_OBJECT &&
                                            Object.class.getName().equals(a.types().argTypeIfNotValue().toString()))) {
                                throw new IllegalArgumentException("" +
                                        "Unsupported attribute: '" + a.javaName() + "' in '" + modelClass + "', " +
                                        "should be Map<String, Object> due to @JsonOthers annotation.");
                            }
                        })
                        .toList())
                .orElse(List.of());

        final var out = generateCodec(modelClass, params);
        if (jsonSchemas != null) {
            // we are only responsible to generate the "self" schema since we assume relationships/other models
            // got their own generated schema using the same $id/$ref convention
            final var fqn = (packagePrefix + className).replace('$', '.');
            jsonSchemas.put(fqn, generateSchema(fqn, params));
        }
        return new GeneratedClass(packagePrefix + className + SUFFIX, out.toString());
    }

    private JsonSchema generateSchema(final String fqn, final List<Param> params) {
        return new JsonSchema(
                null,
                fqn,
                "object",
                null, null, null, null,
                params.stream().collect(toMap(Param::jsonName, Param::schema)),
                null,
                fqn.substring(Math.max(fqn.lastIndexOf('$'), fqn.lastIndexOf('.')) + 1),
                // todo: add a @JsonSchema annotation which can be set on params or record?
                null, null);
    }

    private StringBuilder generateCodec(final String modelClass, final List<Param> params) {
        final var fallbacks = params.stream().filter(Param::others).toList();
        if (fallbacks.size() > 1) {
            throw new IllegalArgumentException("You can only get a single @JsonOthers per @JsonModel record");
        }

        final var pckPrefix = packageName.isBlank() ? "" : packageName + '.';
        final var out = new StringBuilder();
        if (!packageName.isBlank()) {
            out.append("package ").append(packageName).append(";\n\n");
        }
        out.append("import java.util.function.Function;\n");
        out.append("import java.util.function.IntUnaryOperator;\n\n");

        appendGenerationVersion(out);
        out.append("public class ")
                .append(className).append(SUFFIX)
                .append(" extends ").append(BaseJsonCodec.class.getName())
                .append('<').append(modelClass).append("> {\n");

        // read keys sorted by length so matchString can use the key length as a first discriminator,
        // see Parser.matchString(char[][], IntUnaryOperator) contract (stable sort keeps the declaration
        // order for equal lengths)
        final var namedParams = params.stream()
                .filter(p -> !p.others())
                .sorted(Comparator.comparingInt(p -> p.jsonName().length()))
                .toList();

        // KEYS__ array for parser.matchString()
        out.append("  private static final char[][] KEYS__ = {\n").append(namedParams.stream()
                .map(p -> "    \"" + p.stringEscapedJsonName() + "\".toCharArray()")
                .collect(joining(",\n"))).append("\n  };\n\n");

        // KEYS_OFFSETS__ discriminator for parser.matchString(): a generated switch on the key length
        // returning the index of the first key with that length in KEYS__ (-1 when absent) - no
        // sparse array, no runtime search, the codec is built once at compile time
        out.append("  private static final ").append(IntUnaryOperator.class.getName()).append(" KEYS_OFFSETS__ = length -> switch (length) {\n");
        for (int i = 0; i < namedParams.size(); i++) {
            final var p = namedParams.get(i);
            final var length = p.jsonName().length();
            if (i == 0 || length != namedParams.get(i - 1).jsonName().length()) {
                out.append("    case ").append(length).append(" -> ").append(i).append(";\n");
            }
        }
        out.append("    default -> -1;\n  };\n\n");

        // FIELDS__ array (KEYS__ order, @JsonOthers appended at the end)
        out.append("  @SuppressWarnings({\"unchecked\", \"rawtypes\"})\n")
                .append("  private static final ").append(BaseJsonCodec.class.getName()).append(".FieldMeta<").append(modelClass).append(">[] FIELDS__ = new ")
                .append(BaseJsonCodec.class.getName()).append(".FieldMeta[] {\n");
        for (int i = 0; i < namedParams.size(); i++) {
            final var p = namedParams.get(i);
            out.append("    new ").append(BaseJsonCodec.class.getName()).append(".FieldMeta<>(\n")
                    .append("      \"").append(p.stringEscapedJsonName()).append("\".toCharArray(), ")
                    .append(params.indexOf(p)).append(", ")
                    .append(BaseJsonCodec.class.getName()).append(".ContainerKind.").append(containerKind(p.types().paramType())).append(", ")
                    .append(BaseJsonCodec.class.getName()).append(".ValueKind.").append(valueKind(p.types().paramTypeDef())).append(", ")
                    .append(isJavaLangWrapper(p.type())).append(", ")
                    .append(p.others()).append(", ")
                    .append(delegateTypeExpr(p)).append(", ")
                    .append("m -> ((").append(modelClass).append(") m).").append(p.javaName()).append("(), ")
                    .append(p.order()).append(", ")
                    .append("(\"\\\"\" + ").append("\"").append(p.stringEscapedJsonName()).append("\"").append(" + \"\\\":\").toCharArray()")
                    .append(")");
            if (i < namedParams.size() - 1 || !fallbacks.isEmpty()) {
                out.append(',');
            }
            out.append('\n');
        }
        // append @JsonOthers field at the end of FIELDS__ (slotIndex = record declaration position)
        if (!fallbacks.isEmpty()) {
            final var othersParam = fallbacks.get(0);
            final var othersSlot = params.indexOf(othersParam);
            out.append("    new ").append(BaseJsonCodec.class.getName()).append(".FieldMeta<>(\n")
                    .append("      null, ")
                    .append(othersSlot).append(", ")
                    .append(BaseJsonCodec.class.getName()).append(".ContainerKind.").append(containerKind(othersParam.types().paramType())).append(", ")
                    .append(BaseJsonCodec.class.getName()).append(".ValueKind.").append(valueKind(othersParam.types().paramTypeDef())).append(", ")
                    .append("false, true, ")
                    .append(delegateTypeExpr(othersParam)).append(", ")
                    .append("m -> ((").append(modelClass).append(") m).").append(othersParam.javaName()).append("(), ")
                    .append(othersParam.order()).append(", ")
                    .append("null")
                    .append(")");
            out.append('\n');
        }
        out.append("  };\n\n");

        // FIELDS_WRITE__ array (write order = @JsonProperty.order then javaName)
        final var othersIndex = fallbacks.isEmpty() ? -1 : params.indexOf(fallbacks.get(0));
        final var writeOrdered = params.stream()
                .sorted(Comparator.<Param, Integer>comparing(p -> p.order() != Integer.MIN_VALUE ?
                                p.order() :
                                (p.others() ? Integer.MIN_VALUE + 2 : Integer.MIN_VALUE + 1))
                        .thenComparing(Param::javaName))
                .toList();
        out.append("  @SuppressWarnings({\"unchecked\", \"rawtypes\"})\n")
                .append("  private static final ").append(BaseJsonCodec.class.getName()).append(".FieldMeta<").append(modelClass).append(">[] FIELDS_WRITE__ = new ")
                .append(BaseJsonCodec.class.getName()).append(".FieldMeta[] {\n");
        for (int i = 0; i < writeOrdered.size(); i++) {
            final var p = writeOrdered.get(i);
            if (p.others()) {
                out.append("    new ").append(BaseJsonCodec.class.getName()).append(".FieldMeta<>(\n")
                        .append("      \"").append(p.stringEscapedJsonName()).append("\".toCharArray(), ")
                        .append(othersIndex).append(", ")
                        .append(BaseJsonCodec.class.getName()).append(".ContainerKind.").append(containerKind(p.types().paramType())).append(", ")
                        .append(BaseJsonCodec.class.getName()).append(".ValueKind.").append(valueKind(p.types().paramTypeDef())).append(", ")
                        .append(isJavaLangWrapper(p.type())).append(", true, ")
                        .append(delegateTypeExpr(p)).append(", ")
                        .append("m -> ((").append(modelClass).append(") m).").append(p.javaName()).append("(), ")
                        .append(p.order()).append(", ")
                        .append("(\"\\\"\" + ").append("\"").append(p.stringEscapedJsonName()).append("\"").append(" + \"\\\":\").toCharArray()")
                        .append(")");
            } else {
                final var fieldArrayIndex = namedParams.indexOf(p);
                out.append("    FIELDS__[").append(fieldArrayIndex).append("]");
            }
            if (i < writeOrdered.size() - 1) {
                out.append(',');
            }
            out.append('\n');
        }
        out.append("  };\n\n");

        // factory method with @SuppressWarnings("unchecked")
        final boolean hasUnchecked = params.stream().anyMatch(p -> p.type().toString().contains("<"));
        if (hasUnchecked) {
            out.append("  @SuppressWarnings(\"unchecked\")\n");
        }
        out.append("  private static ").append(modelClass.replace('$', '.')).append(" createFromSlots(final Object[] args) {\n");
        out.append("    return new ").append(modelClass.replace('$', '.')).append("(\n");
        for (int i = 0; i < params.size(); i++) {
            final var p = params.get(i);
            final var typeStr = p.type().toString();
            final boolean isObject = typeStr.equals(Object.class.getName()) ||
                    (p.types().paramTypeDef() == ParamTypeDef.GENERIC_OBJECT && p.types().paramType() == ParamType.VALUE);
            if (isObject) {
                out.append("      args[").append(i).append("]");
            } else {
                out.append("      (").append(typeStr.replace('$', '.')).append(") args[").append(i).append("]");
            }
            if (i < params.size() - 1) {
                out.append(',');
            }
            out.append('\n');
        }
        out.append("    );\n  }\n\n");

        // FACTORY__ field
        out.append("  private static final Function<Object[], ").append(modelClass.replace('$', '.')).append("> FACTORY__ = ")
                .append(className).append(SUFFIX).append("::createFromSlots;\n\n");

        // constructor
        out.append("  public ").append(className).append(SUFFIX).append("() {\n");
        out.append("    super(").append(modelClass).append(".class);\n");
        out.append("  }\n\n");

        // read()
        out.append("  @Override\n");
        out.append("  public ").append(modelClass.replace('$', '.')).append(" read(final ")
                .append(JsonCodec.DeserializationContext.class.getName().replace('$', '.')).append(" context)")
                .append(" throws ").append(IOException.class.getName()).append(" {\n");
        out.append("    return readObject(context, KEYS__, KEYS_OFFSETS__, FIELDS__, FACTORY__);\n");
        out.append("  }\n\n");

        // write()
        out.append("  @Override\n");
        out.append("  public void write(final ").append(modelClass.replace('$', '.')).append(" instance, final ")
                .append(JsonCodec.SerializationContext.class.getName().replace('$', '.')).append(" context")
                .append(") throws ").append(IOException.class.getName()).append(" {\n");
        out.append("    writeObject(instance, context, FIELDS_WRITE__);\n");
        out.append("  }\n");

        // nested bean
        if (generateBean) {
            final var codecName = className + SUFFIX;
            out.append("\n");
            out.append("  public static class ").append(FusionBean.class.getSimpleName()).append(" extends ")
                    .append(BaseBean.class.getName()).append("<").append(codecName).append("> {\n");
            out.append("    public ").append(FusionBean.class.getSimpleName()).append("() {\n");
            out.append("      super(")
                    .append(codecName).append(".class, ")
                    .append(DefaultScoped.class.getName()).append(".class, ")
                    .append("1000, ")
                    .append(Map.class.getName()).append(".of());\n");
            out.append("    }\n\n");
            out.append("    @Override\n");
            out.append("    public ").append(codecName).append(" create(final ").append(RuntimeContainer.class.getName())
                    .append(" container, final ")
                    .append(List.class.getName()).append("<").append(Instance.class.getName()).append("<?>> dependents) {\n");
            out.append("      return new ").append(codecName).append("();\n");
            out.append("    }\n");
            out.append("  }\n");
        }
        out.append("}\n\n");
        return out;
    }

    private static String containerKind(final ParamType type) {
        return switch (type) {
            case VALUE -> "VALUE";
            case LIST -> "LIST";
            case SET -> "SET";
            case MAP -> "MAP";
            case MAP_LIST -> "MAP_LIST";
        };
    }

    private static String valueKind(final ParamTypeDef def) {
        return switch (def) {
            case BOOLEAN -> "BOOLEAN";
            case BIG_DECIMAL -> "BIG_DECIMAL";
            case INTEGER -> "INTEGER";
            case LONG -> "LONG";
            case DOUBLE -> "DOUBLE";
            case STRING -> "STRING";
            case ENUM -> "ENUM";
            case LOCAL_DATE -> "LOCAL_DATE";
            case LOCAL_DATE_TIME -> "LOCAL_DATE_TIME";
            case OFFSET_DATE_TIME -> "OFFSET_DATE_TIME";
            case ZONED_DATE_TIME -> "ZONED_DATE_TIME";
            case GENERIC_OBJECT -> "GENERIC_OBJECT";
            case MODEL -> "MODEL";
        };
    }

    private static boolean isJavaLangWrapper(final TypeMirror type) {
        return type instanceof DeclaredType dt &&
                dt.asElement() instanceof TypeElement te &&
                te.getQualifiedName().toString().startsWith("java.lang.");
    }

    private String delegateTypeExpr(final Param param) {
        final var pt = param.types().paramType();
        final var ptd = param.types().paramTypeDef();
        if (pt != ParamType.VALUE && param.types().argTypeIfNotValue() != null) {
            final var raw = rawTypeString(param.types().argTypeIfNotValue().toString());
            return raw + ".class";
        }
        return switch (ptd) {
            case STRING, INTEGER, LONG, DOUBLE, BOOLEAN -> "null";
            case ENUM -> ParsedType.of(param.type()).className() + ".class";
            case BIG_DECIMAL -> BigDecimal.class.getName() + ".class";
            case LOCAL_DATE -> LocalDate.class.getName() + ".class";
            case LOCAL_DATE_TIME -> LocalDateTime.class.getName() + ".class";
            case OFFSET_DATE_TIME -> OffsetDateTime.class.getName() + ".class";
            case ZONED_DATE_TIME -> ZonedDateTime.class.getName() + ".class";
            case MODEL -> param.type().toString().replace('$', '.') + ".class";
            case GENERIC_OBJECT -> Object.class.getName() + ".class";
        };
    }

    private static String rawTypeString(final String type) {
        final var generics = type.indexOf('<');
        return generics < 0 ? type : type.substring(0, generics);
    }

    private ParamTypes typeOf(final String typeString, final TypeMirror raw) { // todo: enhance error cases
        if (typeString.startsWith(LIST_PREFIX) && typeString.endsWith(">")) {
            final var arg = ((DeclaredType) raw).getTypeArguments().get(0);
            return new ParamTypes(ParamType.LIST, ParamTypeDef.of(
                    typeString.substring(LIST_PREFIX.length(), typeString.length() - ">".length()),
                    processingEnv.getTypeUtils().asElement(arg),
                    models), arg);
        }
        if (typeString.startsWith(COLLECTION_PREFIX) && typeString.endsWith(">")) {
            final var arg = ((DeclaredType) raw).getTypeArguments().get(0);
            return new ParamTypes(ParamType.LIST, ParamTypeDef.of(
                    typeString.substring(COLLECTION_PREFIX.length(), typeString.length() - ">".length()),
                    processingEnv.getTypeUtils().asElement(arg),
                    models), arg);
        }
        if (typeString.startsWith(SET_PREFIX) && typeString.endsWith(">")) {
            final var arg = ((DeclaredType) raw).getTypeArguments().get(0);
            return new ParamTypes(ParamType.SET, ParamTypeDef.of(
                    typeString.substring(SET_PREFIX.length(), typeString.length() - ">".length()),
                    processingEnv.getTypeUtils().asElement(arg),
                    models), arg);
        }
        if (typeString.startsWith(MAP_STRING_PREFIX) && typeString.endsWith(">")) {
            final var arg = ((DeclaredType) raw).getTypeArguments().get(1);
            final var nestedType = typeString.substring(MAP_STRING_PREFIX.length(), typeString.length() - ">".length()).strip();
            if (nestedType.contains("<")) {
                final var nested = typeOf(nestedType, arg);
                if (nested.paramType() == ParamType.LIST) {
                    return new ParamTypes(ParamType.MAP_LIST, nested.paramTypeDef(), nested.argTypeIfNotValue());
                }
            }
            return new ParamTypes(ParamType.MAP, ParamTypeDef.of(
                    nestedType,
                    processingEnv.getTypeUtils().asElement(arg),
                    models), arg);
        }
        return new ParamTypes(ParamType.VALUE, ParamTypeDef.of(typeString, processingEnv.getTypeUtils().asElement(raw), models), null);
    }

    // the read() switch matches keys by their index in KEYS__ (sorted by length), see Parser.matchString(char[][], IntUnaryOperator)
    private String keyCase(final List<Param> params, final Param param) {
        return "            case " + params.indexOf(param) + ": // " + param.stringEscapedJsonName() + "\n";
    }

    private String dateClassOf(final Param it) {
        return switch (it.types().paramTypeDef()) {
            case LOCAL_DATE -> LocalDate.class.getName();
            case LOCAL_DATE_TIME -> LocalDateTime.class.getName();
            case OFFSET_DATE_TIME -> OffsetDateTime.class.getName();
            case ZONED_DATE_TIME -> ZonedDateTime.class.getName();
            default -> throw new IllegalStateException("Unsupported parameter: " + it + " from " + element);
        };
    }

    private record Param(String javaName, String jsonName, TypeMirror type,
                         ParamTypes types, boolean others, String doc, int order,
                         String stringEscapedJsonName) {
        private Param(final String javaName, final String jsonName, final TypeMirror type,
                      final ParamTypes types, final boolean others, final String doc, final int order) {
            this(javaName, jsonName, type, types, others, doc, order, escapeJsonName(jsonName));
        }

        // computed once, it is used by most generation branches
        private static String escapeJsonName(final String jsonName) {
            return jsonName
                    .replace("\"", "\\\"")
                    .replace("\\", "\\\\")
                    .replace("\b", "\\\b")
                    .replace("\f", "\\\f")
                    .replace("\n", "\\\n")
                    .replace("\r", "\\\r");
        }

        public String defaultValue() {
            return switch (types.paramType()) {
                case VALUE -> switch (types.paramTypeDef()) {
                    case LONG -> isWrapper() ? "null" : "0L";
                    case DOUBLE -> isWrapper() ? "null" : "0.";
                    case INTEGER -> isWrapper() ? "null" : "0";
                    case BOOLEAN -> isWrapper() ? "null" : "false";
                    default -> "null";
                };
                case MAP, LIST, SET, MAP_LIST -> "null";
            };
        }

        private boolean isWrapper() {
            return type instanceof DeclaredType dt &&
                    dt.asElement() instanceof TypeElement te &&
                    te.getQualifiedName().toString().startsWith("java.lang.");
        }

        public JsonSchema schema() {
            return switch (types.paramType()) {
                case VALUE -> valueSchema();
                case MAP -> new JsonSchema(
                        null, null, "object", true, null, null,
                        valueSchema().asMap(), null, null, null, description(), null);
                case LIST, SET -> new JsonSchema(
                        null, null, "array", null, null, null,
                        null, null, valueSchema(), null, description(), null);
                case MAP_LIST -> new JsonSchema(
                        null, null, "object", true, null, null,
                        new JsonSchema(
                                null, null, "array", null, null, null,
                                null, null, valueSchema()).asMap(), null, null,
                        null, description(), null);
            };
        }

        private String description() {
            return doc;
        }

        private JsonSchema valueSchema() {
            final var testedType = ofNullable(types.argTypeIfNotValue()).orElse(type()).toString().replace('$', '.');
            return switch (types.paramTypeDef()) {
                case BOOLEAN ->
                        new JsonSchema(null, null, "boolean", !"boolean".equals(testedType), null, null, null, null, null, null, description(), null);
                case INTEGER ->
                        new JsonSchema(null, null, "integer", !"int".equals(testedType), "int32", null, null, null, null, null, description(), null);
                case LONG ->
                        new JsonSchema(null, "number", "integer", !"long".equals(testedType), "int64", null, null, null, null, null, description(), null);
                case DOUBLE ->
                        new JsonSchema(null, "number", "integer", !"double".equals(testedType), null, null, null, null, null, null, description(), null);
                // there is not yet a "decimal" format but number is not safe enough for big_decimal
                case BIG_DECIMAL ->
                        new JsonSchema(null, null, "string", true, null, null, null, null, null, null, description(), null);
                case STRING ->
                        new JsonSchema(null, null, "string", true, null, null, null, null, null, null, description(), null);
                case ENUM -> new JsonSchema(
                        null, null, "string", true,
                        null, null, null, null, null, null, description(),
                        type == null ? null : ParsedType.of(type).enumValues());
                case LOCAL_DATE ->
                        new JsonSchema(null, null, "string", true, "date", null, null, null, null, null, description(), null);
                case LOCAL_DATE_TIME ->
                        new JsonSchema(null, null, "string", true, null, "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\.[0-9]*)?$", null, null, null, null, description(), null);
                case OFFSET_DATE_TIME ->
                        new JsonSchema(null, null, "string", true, null, "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?([+-]?[0-9]{2}:[0-9]{2})?Z?$", null, null, null, null, description(), null);
                case ZONED_DATE_TIME ->
                        new JsonSchema(null, null, "string", true, null, "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\.[0-9]*)?([+-]?[0-9]{2}:[0-9]{2})?Z?(\\[.*\\])?$", null, null, null, null, description(), null);
                case MODEL ->
                        new JsonSchema("#/schemas/" + testedType, null, null, true, null, null, null, null, null, null, description(), null);
                case GENERIC_OBJECT ->
                        new JsonSchema(null, null, "object", true, null, null, true, null, null, null, description(), null);
            };
        }
    }

    private record ParamTypes(ParamType paramType, ParamTypeDef paramTypeDef, TypeMirror argTypeIfNotValue) {
    }

    private enum ParamType {
        VALUE,
        LIST,
        SET,
        MAP,
        MAP_LIST // tolerated
    }

    private enum ParamTypeDef { // a codec exists
        BOOLEAN,
        BIG_DECIMAL,
        INTEGER,
        LONG,
        DOUBLE,
        STRING,
        ENUM, // todo: create a codec implicitly or enable to use one if any?
        LOCAL_DATE,
        LOCAL_DATE_TIME,
        OFFSET_DATE_TIME,
        ZONED_DATE_TIME,
        GENERIC_OBJECT,
        MODEL; // Map<String, Object> indirectly

        public static ParamTypeDef of(final String name, final Element type, final Collection<String> models) {
            return switch (name) {
                case "boolean", "java.lang.Boolean" -> BOOLEAN;
                case "java.math.BigDecimal" -> BIG_DECIMAL;
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
                    if (type.getKind() == RECORD &&
                            (type.getAnnotation(JsonModel.class) != null || models.contains(((TypeElement) type).getQualifiedName().toString()))) {
                        yield MODEL;
                    }
                    if (type.getKind() == ElementKind.ENUM) {
                        yield ENUM;
                    }
                    throw new IllegalArgumentException("Unsupported type: '" + name + "', known models: " + models);
                }
            };
        }
    }
}
