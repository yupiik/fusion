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

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.fusion.framework.build.api.json.JsonOthers;
import io.yupiik.fusion.framework.build.api.json.JsonProperty;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.ParsedType;
import io.yupiik.fusion.framework.processor.internal.meta.JsonSchema;
import io.yupiik.fusion.json.internal.JsonStrings;
import io.yupiik.fusion.json.internal.codec.BaseJsonCodec;
import io.yupiik.fusion.json.internal.codec.CollectionJsonCodec;
import io.yupiik.fusion.json.internal.codec.MapJsonCodec;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static javax.lang.model.element.ElementKind.RECORD;

public class JsonCodecGenerator extends BaseGenerator implements Supplier<BaseGenerator.GeneratedClass> {
    public static final String SUFFIX = "$FusionJsonCodec";

    private final String packageName;
    private final String className;
    private final TypeElement element;
    private final Collection<String> models;
    private final Map<String, JsonSchema> jsonSchemas;

    public JsonCodecGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                              final String packageName, final String className, final TypeElement element,
                              final Collection<String> models, final Map<String, JsonSchema> jsonSchemasCollector) {
        super(processingEnv, elements);
        this.packageName = packageName;
        this.className = className;
        this.element = element;
        this.models = models;
        this.jsonSchemas = jsonSchemasCollector;
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
        final var valueParams = params.stream()
                .filter(it -> it.types().paramType() == ParamType.VALUE)
                .toList();
        final var strings = valueParams.stream().filter(p -> {
            final var def = p.types().paramTypeDef();
            return def == ParamTypeDef.STRING || def == ParamTypeDef.BIG_DECIMAL || def == ParamTypeDef.ENUM;
        }).toList();
        final var numbers = valueParams.stream().filter(p -> {
            final var def = p.types().paramTypeDef();
            return def == ParamTypeDef.INTEGER || def == ParamTypeDef.LONG || def == ParamTypeDef.DOUBLE || def == ParamTypeDef.BIG_DECIMAL;
        }).toList();
        final var booleans = valueParams.stream().filter(p -> p.types().paramTypeDef() == ParamTypeDef.BOOLEAN).toList();
        final var dates = valueParams.stream().filter(p -> {
            final var def = p.types().paramTypeDef();
            return def == ParamTypeDef.LOCAL_DATE || def == ParamTypeDef.LOCAL_DATE_TIME ||
                    def == ParamTypeDef.OFFSET_DATE_TIME || def == ParamTypeDef.ZONED_DATE_TIME;
        }).toList();
        final var models = valueParams.stream().filter(p -> p.types().paramTypeDef() == ParamTypeDef.MODEL).toList();
        final var genericObjects = valueParams.stream().filter(p -> p.types().paramTypeDef() == ParamTypeDef.GENERIC_OBJECT).toList();
        final var collections = params.stream()
                .filter(it -> {
                    final var paramType = it.types().paramType();
                    return paramType == ParamType.SET || paramType == ParamType.LIST;
                })
                .toList();
        final var maps = params.stream().filter(it -> it.types().paramType() == ParamType.MAP && !it.others()).toList();
        final var fallbacks = params.stream().filter(Param::others).toList();

        if (fallbacks.size() > 1) {
            throw new IllegalArgumentException("You can only get a single @JsonOthers per @JsonModel record");
        }

        final var commaAppender = params.size() > 1 ? """
                      if (firstAttribute) {
                        firstAttribute = false;
                      } else {
                        writer.write(',');
                      }
                """ :
                "";
        final var firstCommaHandler = params.size() > 1 ? "      firstAttribute = false;\n" : "";

        final var createIfNullFallbackMap = fallbacks.stream().findFirst().map(a -> "" +
                        "if (param__" + a.javaName() + " == null) {\n" +
                        "  param__" + a.javaName() + " = new " + LinkedHashMap.class.getName() + "<String, Object>();\n" +
                        "}\n")
                .orElse(null);

        final var out = new StringBuilder();
        if (!packageName.isBlank()) {
            out.append("package ").append(packageName).append(";\n\n");
        }

        appendGenerationVersion(out);
        out.append("public class ")
                .append(className).append(SUFFIX)
                .append(" extends ").append(BaseJsonCodec.class.getName())
                .append('<').append(modelClass).append("> {\n");
        out.append(params.stream()
                .map(p -> "  private static final char[] " + p.javaName() + "__CHAR_ARRAY = \"\\\"" + p.stringEscapedJsonName() + "\\\":\".toCharArray();")
                .collect(joining("\n", "", "\n\n")));
        out.append("  public ").append(className).append(SUFFIX).append("() {\n");
        out.append("    super(").append(modelClass).append(".class);\n");
        out.append("  }\n");
        out.append("\n");
        out.append("  @Override\n");
        out.append("  public ").append(className.replace('$', '.')).append(" read(")
                .append(JsonCodec.DeserializationContext.class.getName().replace('$', '.')).append(" context) throws ").append(IOException.class.getName()).append(" {\n");
        out.append("    final var parser = context.parser();\n");
        out.append("    parser.enforceNext(").append(Parser.class.getName()).append(".Event.START_OBJECT);\n");
        out.append("\n");
        out.append(params.stream()
                .map(it -> "    " + it.type() + " param__" + it.javaName() + " = " + it.defaultValue() + ";\n")
                .collect(joining()));
        out.append("\n");
        out.append("    String key = null;\n");
        out.append("    ").append(Parser.class.getName()).append(".Event event = null;\n");
        out.append("    while (parser.hasNext()) {\n");
        out.append("      event = parser.next();\n");
        out.append("      switch (event) {\n");
        out.append("        case KEY_NAME: key = parser.getString(); break;\n");
        if (!strings.isEmpty() || !dates.isEmpty() || !fallbacks.isEmpty() || !genericObjects.isEmpty()) {
            out.append("        case VALUE_STRING:\n");
            out.append("          switch (key) {\n");
            out.append(strings.stream()
                    .map(it -> {
                        final var assignment = "              param__" + it.javaName() + " = ";
                        return "" +
                                "            case \"" + it.stringEscapedJsonName() + "\":\n" +
                                switch (it.types().paramTypeDef()) {
                                    case STRING -> assignment + "parser.getString();\n";
                                    case ENUM -> "              parser.rewind(event);\n" +
                                            assignment + "context.codec(" + ParsedType.of(it.type()).className() + ".class).read(context);\n";
                                    case BIG_DECIMAL -> "              parser.rewind(event);\n" +
                                            assignment + "context.codec(" + BigDecimal.class.getName() + ".class).read(context);\n";
                                    default ->
                                            throw new IllegalStateException("Unsupported parameter: " + it + " from " + element);
                                } +
                                "              break;\n";
                    })
                    .collect(joining()));
            out.append(dates.stream()
                    .map(it -> "" +
                            "            case \"" + it.stringEscapedJsonName() + "\":\n" +
                            "              parser.rewind(event);\n" +
                            "              param__" + it.javaName() + " = context.codec(" +
                            switch (it.types().paramTypeDef()) {
                                case LOCAL_DATE -> LocalDate.class.getName();
                                case LOCAL_DATE_TIME -> LocalDateTime.class.getName();
                                case OFFSET_DATE_TIME -> OffsetDateTime.class.getName();
                                case ZONED_DATE_TIME -> ZonedDateTime.class.getName();
                                default ->
                                        throw new IllegalStateException("Unsupported parameter: " + it + " from " + element);
                            } + ".class).read(context);\n" +
                            "              break;\n")
                    .collect(joining()));
            out.append(genericObjects.stream()
                    .map(it -> "" +
                            "            case \"" + it.stringEscapedJsonName() + "\":\n" +
                            "              param__" + it.javaName() + " = parser.getString();\n" +
                            "              break;\n")
                    .collect(joining()));
            if (fallbacks.isEmpty()) {
                out.append("            default: // ignore\n");
            } else {
                out.append("            default:\n");
                out.append(createIfNullFallbackMap.indent(14));
                out.append("              param__").append(fallbacks.get(0).javaName()).append(".put(key, parser.getString());\n");
            }
            out.append("          }\n");
            out.append("          key = null;\n");
            out.append("          break;\n");
        }
        if (!numbers.isEmpty() || !fallbacks.isEmpty() || !genericObjects.isEmpty()) {
            out.append("        case VALUE_NUMBER:\n");
            out.append("          switch (key) {\n");
            out.append(numbers.stream()
                    .map(it -> "" +
                            "            case \"" + it.stringEscapedJsonName() + "\":\n" +
                            "              param__" + it.javaName() + " = parser." +
                            switch (it.types().paramTypeDef()) {
                                case INTEGER -> "getInt";
                                case LONG -> "getLong";
                                case DOUBLE -> "getDouble";
                                case BIG_DECIMAL -> "getBigDecimal";
                                default ->
                                        throw new IllegalStateException("Unsupported parameter: " + it + " from " + element);
                            } + "();\n" +
                            "              break;\n")
                    .collect(joining()));
            out.append(genericObjects.stream()
                    .map(it -> "" +
                            "            case \"" + it.stringEscapedJsonName() + "\":\n" +
                            "              param__" + it.javaName() + " = parser.getBigDecimal();\n" +
                            "              break;\n")
                    .collect(joining()));
            if (fallbacks.isEmpty()) {
                out.append("            default: // ignore\n");
            } else {
                out.append("            default:\n");
                out.append(createIfNullFallbackMap.indent(14));
                out.append("              param__").append(fallbacks.get(0).javaName()).append(".put(key, parser.getBigDecimal());\n");
            }
            out.append("          }\n");
            out.append("          key = null;\n");
            out.append("          break;\n");
        }
        if (!booleans.isEmpty() || !fallbacks.isEmpty() || !genericObjects.isEmpty()) {
            out.append("        case VALUE_TRUE:\n");
            out.append("        case VALUE_FALSE:\n");
            out.append("          switch (key) {\n");
            out.append(Stream.concat(booleans.stream(), genericObjects.stream())
                    .map(it -> "" +
                            "            case \"" + it.stringEscapedJsonName() + "\":\n" +
                            "              param__" + it.javaName() + " = " + Parser.class.getName() + ".Event.VALUE_TRUE.equals(event);\n" +
                            "              break;\n")
                    .collect(joining()));
            if (fallbacks.isEmpty()) {
                out.append("            default: // ignore\n");
            } else {
                out.append("            default:\n");
                out.append(createIfNullFallbackMap.indent(14));
                out.append("              param__").append(fallbacks.get(0).javaName())
                        .append(".put(key, ").append(Parser.class.getName()).append(".Event.VALUE_TRUE.equals(event));\n");
            }
            out.append("          }\n");
            out.append("          key = null;\n");
            out.append("          break;\n");
        }
        if (!models.isEmpty() || !genericObjects.isEmpty() || !maps.isEmpty() || !fallbacks.isEmpty()) {
            out.append("        case START_OBJECT:\n");
            out.append("          switch (key) {\n");
            out.append(models.stream()
                    .map(it -> "" +
                            "            case \"" + it.stringEscapedJsonName() + "\":\n" +
                            "              parser.rewind(event);\n" +
                            "              param__" + it.javaName() + " = context.codec(" + it.type() + ".class).read(context);\n" +
                            "              break;\n")
                    .collect(joining()));
            out.append(genericObjects.stream()
                    .map(it -> "" +
                            "            case \"" + it.stringEscapedJsonName() + "\":\n" +
                            "              parser.rewind(event);\n" +
                            "              param__" + it.javaName() + " = context.codec(" + Object.class.getName() + ".class).read(context);\n" +
                            "              break;\n")
                    .collect(joining()));
            out.append(maps.stream()
                    .map(it -> "" +
                            "            case \"" + it.stringEscapedJsonName() + "\":\n" +
                            "              parser.rewind(event);\n" +
                            "              param__" + it.javaName() + " = new " + MapJsonCodec.class.getName() + "<>(context.codec(" +
                            it.types().argTypeIfNotValue() + ".class)).read(context);\n" +
                            "              break;\n")
                    .collect(joining()));
            if (fallbacks.isEmpty()) {
                out.append("            default:\n              parser.skipObject();\n              break;\n");
            } else {
                out.append("            default:\n");
                out.append(createIfNullFallbackMap.indent(14));
                out.append("              parser.rewind(event);\n");
                out.append("              param__").append(fallbacks.get(0).javaName())
                        .append(".put(key, context.codec(").append(Object.class.getName()).append(".class).read(context));\n");
            }
            out.append("          }\n");
            out.append("          key = null;\n");
            out.append("          break;\n");
        }
        if (!collections.isEmpty() || !fallbacks.isEmpty() || !genericObjects.isEmpty()) {
            out.append("        case START_ARRAY:\n");
            out.append("          switch (key) {\n");
            out.append(collections.stream()
                    .map(it -> "" +
                            "            case \"" + it.stringEscapedJsonName() + "\":\n" +
                            "              parser.rewind(event);\n" +
                            switch (it.types().paramTypeDef()) {
                                // todo: generate a codec?
                                default ->
                                        "              param__" + it.javaName() + " = new " + CollectionJsonCodec.class.getName() + "<>(" +
                                                "context.codec(" + it.types().argTypeIfNotValue() + ".class), " + switch (it.types().paramType()) {
                                            case LIST -> List.class.getName();
                                            case SET -> Set.class.getName();
                                            default ->
                                                    throw new IllegalStateException("Unsupported parameter: " + it + " from " + element);
                                        } + ".class, " + switch (it.types().paramType()) {
                                            case LIST -> ArrayList.class.getName();
                                            case SET -> HashSet.class.getName();
                                            default ->
                                                    throw new IllegalStateException("Unsupported parameter: " + it + " from " + element);
                                        } + "::new).read(context);\n";
                            } +
                            "              break;\n")
                    .collect(joining()));
            out.append(genericObjects.stream()
                    .map(it -> "" +
                            "            case \"" + it.stringEscapedJsonName() + "\":\n" +
                            "              parser.rewind(event);\n" +
                            "              param__" + it.javaName() + " = new " + CollectionJsonCodec.class.getName() + "<>(" +
                            "context.codec(java.lang.Object.class), " +
                            List.class.getName() + ".class, " +
                            ArrayList.class.getName() + "::new).read(context);\n" +
                            "              break;\n")
                    .collect(joining()));
            if (fallbacks.isEmpty()) {
                out.append("            default:\n              parser.skipArray();\n              break;\n");
            } else {
                out.append("            default:\n");
                out.append(createIfNullFallbackMap.indent(14));
                out.append("              parser.rewind(event);\n");
                out.append("              param__").append(fallbacks.get(0).javaName())
                        .append(".put(key, context.codec(").append(Object.class.getName()).append(".class).read(context));\n");
            }
            out.append("          }\n");
            out.append("          key = null;\n");
            out.append("          break;\n");
        }
        out.append("        case END_OBJECT: return new ").append(modelClass).append("(").append(params.stream()
                .map(param -> "param__" + param.javaName())
                .collect(joining(", "))).append(");\n");
        out.append("        case ")
                .append(Stream.of(
                                "VALUE_NULL",
                                strings.isEmpty() && dates.isEmpty() && fallbacks.isEmpty() && genericObjects.isEmpty() ? "VALUE_STRING" : null,
                                numbers.isEmpty() && fallbacks.isEmpty() && genericObjects.isEmpty() ? "VALUE_NUMBER" : null,
                                booleans.isEmpty() && fallbacks.isEmpty() && genericObjects.isEmpty() ? "VALUE_TRUE" : null,
                                booleans.isEmpty() && fallbacks.isEmpty() && genericObjects.isEmpty() ? "VALUE_FALSE" : null)
                        .filter(Objects::nonNull)
                        .collect(joining(", ")))
                .append(":\n");
        out.append("          key = null;\n          break;\n");
        if (models.isEmpty() && genericObjects.isEmpty() && maps.isEmpty()) {
            out.append("        case START_OBJECT:\n");
            if (fallbacks.isEmpty()) {
                out.append("          parser.skipObject();\n");
            } else {
                out.append(createIfNullFallbackMap.indent(10));
                out.append("          parser.rewind(event);\n");
                out.append("          param__").append(fallbacks.get(0).javaName())
                        .append(".put(key, context.codec(").append(Object.class.getName()).append(".class).read(context));\n");
            }
            out.append("          key = null;\n          break;\n");
        }
        if (collections.isEmpty() && genericObjects.isEmpty()) {
            out.append("        case START_ARRAY:\n");
            if (fallbacks.isEmpty()) {
                out.append("          parser.skipArray();\n");
            } else {
                out.append(createIfNullFallbackMap.indent(10));
                out.append("          parser.rewind(event);\n");
                out.append("          param__").append(fallbacks.get(0).javaName())
                        .append(".put(key, context.codec(").append(Object.class.getName()).append(".class).read(context));\n");
            }
            out.append("          key = null;\n          break;\n");
        }
        out.append("        // case END_ARRAY: fallthrough\n");
        out.append("        default: throw new IllegalArgumentException(\"Unsupported event: \" + event);\n");
        out.append("      }\n");
        out.append("    }\n");
        out.append("    throw new IllegalArgumentException(\"Object didn't end.\");\n");
        out.append("  }\n");
        out.append("\n");
        out.append("  @Override\n");
        out.append("  public void write(").append(className.replace('$', '.')).append(" instance, ")
                .append(JsonCodec.SerializationContext.class.getName().replace('$', '.')).append(" context) throws ").append(IOException.class.getName()).append(" {\n");
        out.append("    final var writer = context.writer();\n");
        if (params.size() > 1) {
            out.append("    boolean firstAttribute = true;\n");
        }
        out.append("    writer.write('{');\n");

        final var serializerPosition = new AtomicInteger();
        out.append(params.stream()
                .sorted(Comparator.<Param, Integer>comparing(p -> p.order() != Integer.MIN_VALUE ?
                                p.order() :
                                (p.others() ? Integer.MIN_VALUE + 2 : Integer.MIN_VALUE + 1))
                        .thenComparing(Param::javaName))
                .map(param -> {
                    final var paramPosition = serializerPosition.getAndIncrement();
                    final var paramTypeDef = param.types().paramTypeDef();
                    if (param.others()) {
                        final var structure = new StringBuilder();
                        structure.append("    if (instance.").append(param.javaName()).append("() != null) {\n");
                        if (paramPosition > 0) {
                            structure.append(commaAppender);
                        } else {
                            structure.append(firstCommaHandler);
                        }
                        structure.append("      writeJsonOthers(instance.").append(param.javaName()).append("(), context);\n");
                        structure.append("  }\n");
                        return structure.toString();
                    }
                    return switch (param.types().paramType()) { // todo: add a config in @JsonModel to write or not null values (for now ignored)
                        case VALUE -> switch (paramTypeDef) {
                            case INTEGER, LONG, DOUBLE, BOOLEAN -> {
                                final var write = (paramPosition > 0 ? commaAppender : firstCommaHandler) +
                                        "      writer.write(" + param.javaName() + "__CHAR_ARRAY);\n" +
                                        "      writer.write(String.valueOf(instance." + param.javaName() + "()));\n";
                                if (param.type().toString().startsWith("java.lang.")) { // wrapper, can be null
                                    yield "    if (instance." + param.javaName() + "() != null) {\n" + write + "    }\n";
                                }
                                yield write;
                            }
                            case STRING -> "" +
                                    "    if (instance." + param.javaName() + "() != null) {\n" +
                                    (paramPosition > 0 ? commaAppender : firstCommaHandler) +
                                    "      writer.write(" + param.javaName() + "__CHAR_ARRAY);\n" +
                                    "      writer.write(" + JsonStrings.class.getName() + ".escapeChars(instance." + param.javaName() + "()));\n" +
                                    "    }\n";
                            case ENUM, LOCAL_DATE, LOCAL_DATE_TIME, OFFSET_DATE_TIME, ZONED_DATE_TIME, BIG_DECIMAL ->
                                    "" +
                                            "    if (instance." + param.javaName() + "() != null) {\n" +
                                            (paramPosition > 0 ? commaAppender : firstCommaHandler) +
                                            "      writer.write(" + param.javaName() + "__CHAR_ARRAY);\n" +
                                            "      context.codec(" + param.type().toString() + ".class).write(instance." + param.javaName() + "(), context);\n" +
                                            "    }\n";
                            case GENERIC_OBJECT -> "" +
                                    "    if (instance." + param.javaName() + "() != null) {\n" +
                                    (paramPosition > 0 ? commaAppender : firstCommaHandler) +
                                    "      final var codec = context.codec(" + Object.class.getName() + ".class);\n" +
                                    "      writer.write(" + param.javaName() + "__CHAR_ARRAY);\n" +
                                    "      codec.write(instance." + param.javaName() + "(), context);\n" +
                                    "    }\n";
                            case MODEL -> "" +
                                    "    if (instance." + param.javaName() + "() != null) {\n" +
                                    (paramPosition > 0 ? commaAppender : firstCommaHandler) +
                                    "      final var codec = context.codec(" + param.type() + ".class);\n" +
                                    "      writer.write(" + param.javaName() + "__CHAR_ARRAY);\n" +
                                    "      codec.write(instance." + param.javaName() + "(), context);\n" +
                                    "    }\n";
                        };
                        case SET, LIST -> {
                            final var structure = new StringBuilder();
                            structure.append("    if (instance.").append(param.javaName()).append("() != null) {\n");
                            if (paramPosition > 0) {
                                structure.append(commaAppender);
                            } else {
                                structure.append(firstCommaHandler);
                            }
                            structure.append("      writer.write(").append(param.javaName()).append("__CHAR_ARRAY);\n");
                            structure.append("      writer.write('[');\n");
                            structure.append("      final var it = instance.").append(param.javaName()).append("().iterator();\n");
                            if (needsCodec(paramTypeDef)) {
                                structure.append("      final var codec = context.codec(").append(param.types().argTypeIfNotValue()).append(".class);\n");
                            } else if (paramTypeDef == ParamTypeDef.GENERIC_OBJECT) {
                                structure.append("      final var codec = context.codec(").append(Object.class.getName()).append(".class);\n");
                            }
                            structure.append("      while (it.hasNext()) {\n");
                            structure.append("        final var next = it.next();\n");
                            structure.append((switch (paramTypeDef) {
                                case INTEGER, LONG, DOUBLE, BOOLEAN -> "writer.write(String.valueOf(next));\n";
                                case STRING -> "writer.write(next == null ? \"null\" : " +
                                        JsonStrings.class.getName() + ".escapeChars(next));\n";
                                default -> """
                                        if (next == null) {
                                          writer.write(NULL);
                                        } else {
                                          codec.write(next, context);
                                        }
                                        """;
                            }).indent(8));
                            structure.append("        if (it.hasNext()) {\n");
                            structure.append("          writer.write(',');\n");
                            structure.append("        }\n");
                            structure.append("      }\n");
                            structure.append("      writer.write(']');\n");
                            structure.append("    }\n");
                            yield structure.toString();
                        }
                        case MAP -> {
                            final var structure = new StringBuilder();
                            structure.append("    if (instance.").append(param.javaName()).append("() != null) {\n");
                            if (paramPosition > 0) {
                                structure.append(commaAppender);
                            } else {
                                structure.append(firstCommaHandler);
                            }
                            structure.append("      writer.write(").append(param.javaName()).append("__CHAR_ARRAY);\n");
                            structure.append("      writer.write('{');\n");
                            structure.append("      final var it = instance.").append(param.javaName()).append("().entrySet().iterator();\n");
                            if (needsCodec(paramTypeDef)) {
                                structure.append("      final var codec = context.codec(").append(param.types().argTypeIfNotValue()).append(".class);\n");
                            } else if (paramTypeDef == ParamTypeDef.GENERIC_OBJECT) {
                                structure.append("      final var codec = context.codec(").append(Object.class.getName()).append(".class);\n");
                            }
                            structure.append("      while (it.hasNext()) {\n");
                            structure.append("        final var next = it.next();\n");
                            structure.append(("" +
                                    "writer.write(" + JsonStrings.class.getName() + ".escapeChars(next.getKey()));\n" +
                                    "writer.write(':');\n" +
                                    switch (paramTypeDef) {
                                        case INTEGER, LONG, DOUBLE, BOOLEAN ->
                                                "writer.write(String.valueOf(next.getValue()));\n";
                                        case STRING -> """
                                                if (next.getValue() == null) {
                                                  writer.write(NULL);
                                                } else {
                                                  writer.write(io.yupiik.fusion.json.internal.JsonStrings.escapeChars(next.getValue()));
                                                }
                                                """;
                                        default -> """
                                                if (next.getValue() == null) {
                                                  writer.write(NULL);
                                                } else {
                                                  codec.write(next.getValue(), context);
                                                }
                                                """;
                                    }).indent(8));
                            structure.append("        if (it.hasNext()) {\n");
                            structure.append("          writer.write(',');\n");
                            structure.append("        }\n");
                            structure.append("      }\n");
                            structure.append("      writer.write('}');\n");
                            structure.append("    }\n");
                            yield structure.toString();
                        }
                    };
                })
                .collect(joining()));
        out.append("    writer.write('}');\n");
        out.append("  }\n");
        out.append("}\n\n");
        return out;
    }

    private boolean needsCodec(final ParamTypeDef paramTypeDef) {
        return paramTypeDef == ParamTypeDef.MODEL ||
                paramTypeDef == ParamTypeDef.ENUM ||
                paramTypeDef == ParamTypeDef.LOCAL_DATE ||
                paramTypeDef == ParamTypeDef.LOCAL_DATE_TIME ||
                paramTypeDef == ParamTypeDef.OFFSET_DATE_TIME ||
                paramTypeDef == ParamTypeDef.ZONED_DATE_TIME ||
                paramTypeDef == ParamTypeDef.BIG_DECIMAL;
    }

    private ParamTypes typeOf(final String typeString, final TypeMirror raw) { // todo: enhance error cases
        if (typeString.startsWith(List.class.getName() + "<") && typeString.endsWith(">")) {
            final var arg = ((DeclaredType) raw).getTypeArguments().get(0);
            return new ParamTypes(ParamType.LIST, ParamTypeDef.of(
                    typeString.substring((List.class.getName() + "<").length(), typeString.length() - ">".length()),
                    processingEnv.getTypeUtils().asElement(arg),
                    models), arg);
        }
        if (typeString.startsWith(Collection.class.getName() + "<") && typeString.endsWith(">")) {
            final var arg = ((DeclaredType) raw).getTypeArguments().get(0);
            return new ParamTypes(ParamType.LIST, ParamTypeDef.of(
                    typeString.substring((Collection.class.getName() + "<").length(), typeString.length() - ">".length()),
                    processingEnv.getTypeUtils().asElement(arg),
                    models), arg);
        }
        if (typeString.startsWith(Set.class.getName() + "<") && typeString.endsWith(">")) {
            final var arg = ((DeclaredType) raw).getTypeArguments().get(0);
            return new ParamTypes(ParamType.SET, ParamTypeDef.of(
                    typeString.substring((Set.class.getName() + "<").length(), typeString.length() - ">".length()),
                    processingEnv.getTypeUtils().asElement(arg),
                    models), arg);
        }
        if (typeString.startsWith(Map.class.getName() + "<" + String.class.getName() + ",") && typeString.endsWith(">")) {
            final var arg = ((DeclaredType) raw).getTypeArguments().get(1);
            return new ParamTypes(ParamType.MAP, ParamTypeDef.of(
                    typeString.substring((Map.class.getName() + "<" + String.class.getName() + ",").length(), typeString.length() - ">".length()).strip(),
                    processingEnv.getTypeUtils().asElement(arg),
                    models), arg);
        }
        return new ParamTypes(ParamType.VALUE, ParamTypeDef.of(typeString, processingEnv.getTypeUtils().asElement(raw), models), null);
    }

    private record Param(String javaName, String jsonName, TypeMirror type,
                         ParamTypes types, boolean others, String doc, int order) {
        public String defaultValue() {
            return switch (types.paramType()) {
                case VALUE -> switch (types.paramTypeDef()) {
                    case LONG -> isWrapper() ? "null" : "0L";
                    case DOUBLE -> isWrapper() ? "null" : "0.";
                    case INTEGER -> isWrapper() ? "null" : "0";
                    case BOOLEAN -> isWrapper() ? "null" : "false";
                    default -> "null";
                };
                case MAP, LIST, SET -> "null";
            };
        }

        private boolean isWrapper() {
            return type instanceof DeclaredType dt &&
                    dt.asElement() instanceof TypeElement te &&
                    te.getQualifiedName().toString().startsWith("java.lang.");
        }

        public String stringEscapedJsonName() {
            return jsonName
                    .replace("\"", "\\\"")
                    .replace("\\", "\\\\")
                    .replace("\b", "\\\b")
                    .replace("\f", "\\\f")
                    .replace("\n", "\\\n")
                    .replace("\r", "\\\r");
        }

        public JsonSchema schema() {
            return switch (types.paramType()) {
                case VALUE -> valueSchema();
                case MAP -> new JsonSchema(
                        null, null, "object", true, null, null,
                        valueSchema().asMap(), null, null);
                case LIST, SET -> new JsonSchema(
                        null, null, "array", null, null, null,
                        null, null, valueSchema());
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
        MAP
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
                case "java.lang.Object" -> GENERIC_OBJECT;
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
