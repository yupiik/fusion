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
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.build.api.persistence.Column;
import io.yupiik.fusion.framework.build.api.persistence.Id;
import io.yupiik.fusion.framework.build.api.persistence.Table;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.ParsedType;
import io.yupiik.fusion.framework.processor.internal.persistence.SimpleEntity;
import io.yupiik.fusion.persistence.api.PersistenceException;
import io.yupiik.fusion.persistence.impl.BaseEntity;
import io.yupiik.fusion.persistence.impl.ColumnMetadataImpl;
import io.yupiik.fusion.persistence.impl.DatabaseConfiguration;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.yupiik.fusion.framework.processor.internal.stream.Streams.withIndex;
import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

public class PersistenceEntityGenerator extends BaseGenerator implements Supplier<PersistenceEntityGenerator.Output> {
    private static final Set<String> PRIMITIVES = Set.of("int", "boolean", "char", "short", "byte", "long", "double", "float");

    private static final String SUFFIX = "$FusionPersistenceEntity";

    private final Table table;
    private final boolean beanForPersistenceEntities;
    private final String packageName;
    private final String className;
    private final TypeElement type;
    private final TypeMirror onDelete;
    private final TypeMirror onInsert;
    private final TypeMirror onLoad;
    private final TypeMirror onUpdate;
    private final Map<String, SimpleEntity> entities;

    public PersistenceEntityGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                                      final boolean beanForPersistenceEntities, final String packageName,
                                      final String className, final Table table, final TypeElement type,
                                      final TypeMirror onDelete, final TypeMirror onInsert,
                                      final TypeMirror onLoad, final TypeMirror onUpdate,
                                      final Map<String, SimpleEntity> entities) {
        super(processingEnv, elements);
        this.table = table;
        this.beanForPersistenceEntities = beanForPersistenceEntities;
        this.packageName = packageName;
        this.className = className;
        this.type = type;
        this.onDelete = onDelete;
        this.onInsert = onInsert;
        this.onLoad = onLoad;
        this.onUpdate = onUpdate;
        this.entities = entities;
    }

    @Override
    // todo: we parse too often paramter types, make it a sorted map or something like that + support byte[] and other java/sql bindings (bigdecimal, biginteger, java.time)
    public Output get() {
        final var packagePrefix = packageName == null || packageName.isBlank() ? "" : (packageName + '.');
        final var packageLine = packagePrefix.isBlank() ? "" : ("package " + packageName + ";\n\n");
        final var tableClassName = className + SUFFIX;
        final var constructorParameters = findConstructors(type)
                .filter(e -> e.getParameters().size() >= 1)
                .max(RECORD_CONSTRUCTOR_COMPARATOR)
                .orElseThrow(() -> new IllegalArgumentException("@Table classes must have at least one argument constructor: " + packagePrefix + className))
                .getParameters();

        // todo: avoid to visit so often the methods and do a single visitor to capture them all?
        // note: in practise not critical for a record (few methods) but better

        final var onInsertCb = findMethods(type, onInsert).peek(this::isNotPrivate).peek(this::hasNoParam).toList();
        if (onInsertCb.size() > 1) {
            throw new IllegalArgumentException("Multiple @OnInsert were found, this behavior is forbidden for now because not deterministic.");
        }

        final var onUpdateCb = findMethods(type, onUpdate).peek(this::isNotPrivate).peek(this::hasNoParam).toList();
        if (onUpdateCb.size() > 1) {
            throw new IllegalArgumentException("Multiple @OnUpdate were found, this behavior is forbidden for now because not deterministic.");
        }

        final var onDeleteCb = findMethods(type, onDelete).peek(this::isNotPrivate).peek(this::hasNoParam).toList();
        if (onDeleteCb.size() > 1) {
            throw new IllegalArgumentException("Multiple @OnDelete were found, this behavior is forbidden for now because not deterministic.");
        }

        final var onLoadCb = findMethods(type, onLoad).peek(this::isNotPrivate).peek(this::hasNoParam).toList();
        if (onLoadCb.size() > 1) {
            throw new IllegalArgumentException("Multiple @OnLoad were found, this behavior is forbidden for now because not deterministic.");
        }

        final var ids = constructorParameters.stream()
                .filter(it -> it.getAnnotation(Id.class) != null)
                .toList();
        final var standardColumns = constructorParameters.stream()
                .filter(it -> !ids.contains(it)) // @Column is optional for records
                .toList();

        final var idType = switch (ids.size()) {
            case 1 -> ParsedType.of(ids.get(0).asType()).className();
            default -> "java.util.List<Object>"; // composed key
        };

        final var autoIncremented = ids.size() == 1 && ids.get(0).getAnnotation(Id.class).autoIncremented();
        final var tableName = ofNullable(table.value())
                .map(this::escapeString)
                .orElse(className.substring(className.lastIndexOf('.') + 1));

        entities.put(className, new SimpleEntity(tableName, Stream.concat(
                        ids.stream(),
                        standardColumns.stream())
                .map(this::toSimpleColumn)
                .toList()));

        return new Output(new GeneratedClass(packagePrefix + tableClassName,
                packageLine +
                        "\n" +
                        generationVersion() +
                        "public class " + tableClassName + " extends " + BaseEntity.class.getName() + "<" + className + ", " + idType + "> {\n" +
                        "    public " + tableClassName + "(" + DatabaseConfiguration.class.getName() + " configuration) {\n" +
                        "        super(\n" +
                        "          configuration,\n" +
                        "          " + className + ".class,\n" +
                        "          \"" +
                        tableName +
                        "\",\n" +
                        "          java.util.List.of(\n" +
                        Stream.concat(
                                        ids.stream()
                                                .map(id -> newColumnMetadataStart(id) + ", " +
                                                        ids.indexOf(id) + ", " +
                                                        id.getAnnotation(Id.class).autoIncremented() +
                                                        ")"),
                                        standardColumns.stream()
                                                .map(field -> newColumnMetadataStart(field) + ")"))
                                .collect(joining(",\n", "", "\n")) +
                        "          ),\n" +
                        "          " + autoIncremented + ",\n" +
                        "          (" + (onInsertCb.isEmpty() ? "instance" : "entity") + ", statement) -> {\n" +
                        (!onInsertCb.isEmpty() ? "            final var instance = entity." + onInsertCb.get(0).getSimpleName().toString() + "();\n" : "") +
                        withIndex(autoIncremented ? standardColumns.stream() : Stream.concat(ids.stream(), standardColumns.stream()))
                                .map(it -> "            " + jdbcSetter(
                                        it.item(), it.index() + 1,
                                        "instance." + it.item().getSimpleName() + "()"))
                                .collect(joining("\n", "", "\n")) +
                        "            return instance;\n" +
                        "          },\n" +
                        "          (" + (onUpdateCb.isEmpty() ? "instance" : "entity") + ", statement) -> {\n" +
                        (!onUpdateCb.isEmpty() ? "            final var instance = entity." + onUpdateCb.get(0).getSimpleName().toString() + "();\n" : "") +
                        withIndex(standardColumns.stream())
                                .map(it -> "            " + jdbcSetter(
                                        it.item(), it.index() + 1,
                                        "instance." + it.item().getSimpleName() + "()"))
                                .collect(joining("\n", "", "\n")) +
                        withIndex(ids.stream())
                                .map(it -> "            " + jdbcSetter(
                                        it.item(), standardColumns.size() + it.index() + 1,
                                        "instance." + it.item().getSimpleName() + "()"))
                                .collect(joining("\n", "", "\n")) +
                        "            return instance;\n" +
                        "          },\n" +
                        "          (" + (onDeleteCb.isEmpty() ? "instance" : "entity") + ", statement) -> {\n" +
                        (!onDeleteCb.isEmpty() ? "            entity." + onDeleteCb.get(0).getSimpleName().toString() + "();\n" : "") +
                        withIndex(ids.stream())
                                .map(it -> "            " + jdbcSetter(
                                        it.item(), it.index() + 1,
                                        "instance." + it.item().getSimpleName() + "()"))
                                .collect(joining("\n", "", "\n")) +
                        "          },\n" +
                        "          (id, statement) -> {\n" +
                        switch (ids.size()) {
                            case 1 -> "            " + jdbcSetter(ids.get(0), 1, "id") + "\n";
                            // multiple ids, we get a list and we bind each item
                            default -> "            final var it = id.iterator();\n" +
                                    withIndex(ids.stream())
                                            .map(it -> "            " + jdbcSetter(
                                                    it.item(), it.index() + 1,
                                                    "(" + ParsedType.of(it.item().asType()).className() + ") it.next()"))
                                            .collect(joining("\n", "", "\n"));
                        } +
                        "          },\n" +
                        "          (entity, statement) -> " + (!autoIncremented ?
                        "entity" : "{\n" +
                        "            try (final var rset = statement.getGeneratedKeys()) {\n" +
                        "              if (!rset.next()) {\n" +
                        "                throw new " + PersistenceException.class.getName() + "(\"No generated key available\");\n" +
                        "              }\n" +
                        // do a copy of all params except the generated id (can be only one for now)
                        "              return new " + className + "(" + constructorParameters.stream()
                        .map(it -> ids.contains(it) ?
                                jdbcGetter(it, 1) :
                                ("entity." + it.getSimpleName().toString() + "()"))
                        .collect(joining(", ")) + ");\n" +
                        "            }" +
                        "          }") + ",\n" +
                        "          columns -> {\n" +
                        constructorParameters.stream()
                                .map(p -> {
                                    final var leftSide = "            final var " + p.getSimpleName().toString() + " = ";
                                    final var ofPart = "Of(columns.indexOf(\"" + p.getSimpleName().toString() + "\")";
                                    final boolean isEnum = isEnum(p);
                                    final var name = ParsedType.of(p.asType()).className();
                                    if (isEnum) {
                                        return leftSide + "enum" + ofPart + ", " + name + ".class);\n";
                                    }

                                    return leftSide + columnReaderPrefix(p, name) + ofPart + switch (name) {
                                        case "int", "double", "float", "long", "boolean", "byte" -> ", true";
                                        case "java.lang.Integer", "java.lang.Double", "java.lang.Float", "java.lang.Long", "java.lang.Boolean", "java.lang.Byte" ->
                                                ", false";
                                        default -> "";
                                    } + ");\n";
                                })
                                .collect(joining()) +
                        "            return rset -> {\n" +
                        "              try {\n" +
                        "                " + (onLoadCb.isEmpty() ? "return " : "final var entity = ") + "new " + type + "(" + constructorParameters.stream()
                        .map(it -> it.getSimpleName().toString() + ".apply(rset)")
                        .collect(joining(", ")) + ");\n" +
                        (onLoadCb.isEmpty() ?
                                "" :
                                "                entity." + onLoadCb.get(0).getSimpleName().toString() + "();\n                return entity;\n") +
                        "              } catch (final " + SQLException.class.getName() + " e) {\n" +
                        "                throw new " + PersistenceException.class.getName() + "(e);\n" +
                        "              }\n" +
                        "            };\n" +
                        "          });\n" +
                        "    }\n" +
                        "}\n" +
                        "\n"),
                beanForPersistenceEntities ?
                        new GeneratedClass(packagePrefix + tableClassName + '$' + FusionBean.class.getSimpleName(), packageLine +
                                generationVersion() +
                                "public class " + tableClassName + '$' + FusionBean.class.getSimpleName() + " extends " + BaseBean.class.getName() + "<" + tableClassName + "> {\n" +
                                "  public " + tableClassName + '$' + FusionBean.class.getSimpleName() + "() {\n" +
                                "    super(\n" +
                                "      " + tableClassName + ".class,\n" +
                                "      " + DefaultScoped.class.getName() + ".class,\n" +
                                "      " + findPriority(type) + ",\n" +
                                "      " + Map.class.getName() + ".of());\n" +
                                "  }\n" +
                                "\n" +
                                "  @Override\n" +
                                "  public " + tableClassName + " create(final " + RuntimeContainer.class.getName() + " container, final " +
                                List.class.getName() + "<" + Instance.class.getName() + "<?>> dependents) {\n" +
                                "    return new " + tableClassName + "(lookup(container, " + DatabaseConfiguration.class.getName() + ".class, dependents));\n" +
                                "  }\n" +
                                "}\n" +
                                "\n") :
                        null);
    }

    private SimpleEntity.SimpleColumn toSimpleColumn(final VariableElement id) {
        return new SimpleEntity.SimpleColumn(id.getSimpleName().toString(), ofNullable(id.getAnnotation(Column.class))
                .map(Column::name)
                .map(this::escapeString)
                .orElseGet(() -> id.getSimpleName().toString()));
    }

    private String columnReaderPrefix(final VariableElement p, final String name) {
        return switch (name) {
            case "java.util.Date", "java.sql.Date" -> "date";
            case "java.lang.String" -> "string";
            case "java.lang.Integer", "int" -> "int";
            case "java.lang.Double", "double" -> "double";
            case "java.lang.Float", "float" -> "float";
            case "java.lang.Long", "long" -> "long";
            case "java.lang.Boolean", "boolean" -> "boolean";
            case "java.lang.Byte", "byte" -> "byte";
            case "java.lang.Short", "short" -> "short";
            case "java.math.BigDecimal" -> "bigdecimal";
            case "byte[]" -> "bytes";
            case "java.math.BigInteger", "java.time.LocalDate", "java.time.LocalDateTime",
                    "java.time.OffsetDateTime", "java.time.ZonedDateTime", "java.time.LocalTime" ->
                    name.substring(name.lastIndexOf('.') + 1).toLowerCase(ROOT); // object
            default -> throw new IllegalArgumentException("Unsupported type: " + p.asType());
        };
    }

    private String jdbcGetter(final Element elt, final int jdbcIndex) {
        final var type = elt.asType();
        final boolean isEnum = isEnum(elt);
        final var fqn = isEnum ? "java.lang.String" : ParsedType.of(type).className();
        return "rset.get" + jdbcMarker(type, fqn) + "(" + jdbcIndex + ")";
    }

    private String jdbcSetter(final Element elt,
                              final int jdbcIndex,
                              final String accessor) {
        final var type = elt.asType();
        final boolean isEnum = isEnum(elt);
        final var fqn = isEnum ? "java.lang.String" : ParsedType.of(type).className();
        final var handleNull = !PRIMITIVES.contains(fqn);
        return (handleNull ?
                "if (" + accessor + " == null) { " +
                        "statement.setNull(" + jdbcIndex + ", " +
                        switch (fqn) { // https://docs.oracle.com/cd/E19501-01/819-3659/gcmaz/
                            case "java.lang.String" -> "java.sql.Types.VARCHAR";
                            case "java.lang.Integer" -> "java.sql.Types.INTEGER";
                            case "java.lang.Double" -> "java.sql.Types.DOUBLE";
                            case "java.lang.Float" -> "java.sql.Types.FLOAT";
                            case "java.lang.Long", "java.math.BigInteger" -> "java.sql.Types.BIGINT";
                            case "java.lang.Boolean" -> "java.sql.Types.BOOLEAN";
                            case "java.lang.Byte", "java.lang.Short" -> "java.sql.Types.SMALLINT";
                            case "java.util.Date", "java.sql.Date", "java.time.LocalDate", "java.time.LocalDateTime" ->
                                    "java.sql.Types.DATE";
                            case "java.time.OffsetDateTime", "java.time.ZonedDateTime" ->
                                    "java.sql.Types.TIMESTAMP_WITH_TIMEZONE";
                            case "java.time.LocalTime" -> "java.sql.Types.TIME";
                            case "java.math.BigDecimal" -> "java.sql.Types.DECIMAL";
                            case "byte[]" -> "java.sql.Types.VARBINARY";
                            default -> "java.sql.Types.OTHERS";
                        } + "); " +
                        "} else { " :
                "") +
                "statement.set" + jdbcMarker(type, fqn) + "(" + jdbcIndex + ", " + accessor + (isEnum ? ".name()" : "") + ");" +
                (handleNull ? " }" : "");
    }

    private String jdbcMarker(final TypeMirror type, final String fqn) {
        return switch (fqn) {
            case "java.lang.String" -> "String";
            case "java.lang.Integer", "int" -> "Int";
            case "java.lang.Double", "double" -> "Double";
            case "java.lang.Float", "float" -> "Float";
            case "java.lang.Long", "long" -> "Long";
            case "java.lang.Boolean", "boolean" -> "Boolean";
            case "java.lang.Byte", "byte" -> "Byte";
            case "java.lang.Short", "short" -> "Short";
            case "byte[]" -> "Bytes";
            case "java.math.BigDecimal" -> "BigDecimal";
            case "java.util.Date", "java.sql.Date" -> "Date";
            case "java.math.BigInteger", "java.time.LocalDate", "java.time.LocalDateTime",
                    "java.time.OffsetDateTime", "java.time.ZonedDateTime", "java.time.LocalTime" -> "Object";
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };
    }

    private boolean isEnum(final Element elt) {
        return elt.asType() instanceof DeclaredType dt && dt.asElement().getKind() == ElementKind.ENUM;
    }

    private void isNotPrivate(final ExecutableElement executableElement) {
        if (executableElement.getModifiers().contains(Modifier.PRIVATE)) {
            throw new IllegalArgumentException("" +
                    "Forbidden private element: " + executableElement.getEnclosingElement() + "." + executableElement + ", " +
                    "make it protected/package scope at least.");
        }
    }

    private void hasNoParam(final ExecutableElement executableElement) {
        if (!executableElement.getParameters().isEmpty()) {
            throw new IllegalArgumentException("" +
                    "Forbidden parameters: " + executableElement.getEnclosingElement() + "." + executableElement + ".");
        }
    }

    private String newColumnMetadataStart(final Element element) {
        return "            " +
                "new " + ColumnMetadataImpl.class.getName() + "(" +
                "\"" + element.getSimpleName().toString() + "\", " +
                ParsedType.of(element.asType()).className() + ".class, " +
                "\"" + ofNullable(element.getAnnotation(Column.class))
                .map(Column::name)
                .map(this::escapeString)
                .orElseGet(() -> element.getSimpleName().toString()) + "\"";
    }

    private String escapeString(final String it) {
        return it
                .replace("\"", "\\\"")
                .replace("\n", "\\\n");
    }

    public record Output(GeneratedClass entity, GeneratedClass bean) {
    }
}
