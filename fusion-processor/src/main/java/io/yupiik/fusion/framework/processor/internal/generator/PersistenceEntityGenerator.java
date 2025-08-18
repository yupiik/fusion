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
import io.yupiik.fusion.framework.build.api.persistence.Column;
import io.yupiik.fusion.framework.build.api.persistence.Id;
import io.yupiik.fusion.framework.build.api.persistence.Table;
import io.yupiik.fusion.framework.processor.internal.Bean;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.ParsedType;
import io.yupiik.fusion.framework.processor.internal.metadata.MetadataContributorRegistry;
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
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeMirror;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.yupiik.fusion.framework.processor.internal.stream.Streams.withIndex;
import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

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
                                      final MetadataContributorRegistry metadataContributorRegistry,
                                      final boolean beanForPersistenceEntities, final String packageName,
                                      final String className, final Table table, final TypeElement type,
                                      final TypeMirror onDelete, final TypeMirror onInsert,
                                      final TypeMirror onLoad, final TypeMirror onUpdate,
                                      final Map<String, SimpleEntity> entities) {
        super(processingEnv, elements, metadataContributorRegistry);
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
    // todo: we parse too often parameter types, make it a sorted map or something like that + support byte[] and other java/sql bindings (bigdecimal, biginteger, java.time)
    public Output get() {
        final var packagePrefix = packageName == null || packageName.isBlank() ? "" : (packageName + '.');
        final var packageLine = packagePrefix.isBlank() ? "" : ("package " + packageName + ";\n\n");
        final var tableClassName = className + SUFFIX;
        final var constructorParameters = selectConstructorFor(() -> packagePrefix + className, type);

        // todo: avoid to visit so often the methods and do a single visitor to capture them all?
        // note: in practise not critical for a record (few methods) but better

        final var onInsertCb = findMethods(type, onInsert).peek(this::isNotPrivate).toList();
        if (onInsertCb.size() > 1) {
            throw new IllegalArgumentException("Multiple @OnInsert were found, this behavior is forbidden for now because not deterministic.");
        }

        final var onUpdateCb = findMethods(type, onUpdate).peek(this::isNotPrivate).toList();
        if (onUpdateCb.size() > 1) {
            throw new IllegalArgumentException("Multiple @OnUpdate were found, this behavior is forbidden for now because not deterministic.");
        }

        final var onDeleteCb = findMethods(type, onDelete).peek(this::isNotPrivate).toList();
        if (onDeleteCb.size() > 1) {
            throw new IllegalArgumentException("Multiple @OnDelete were found, this behavior is forbidden for now because not deterministic.");
        }

        final var onLoadCb = findMethods(type, onLoad).peek(this::isNotPrivate).toList();
        if (onLoadCb.size() > 1) {
            throw new IllegalArgumentException("Multiple @OnLoad were found, this behavior is forbidden for now because not deterministic.");
        }

        final var ids = constructorParameters.stream()
                .filter(it -> it.getAnnotation(Id.class) != null)
                .toList();
        final var standardColumns = constructorParameters.stream()
                .filter(it -> !ids.contains(it)) // @Column is optional
                .toList();

        final var idType = switch (ids.size()) {
            case 1 -> ParsedType.of(ids.get(0).asType()).className();
            default -> "java.util.List<Object>"; // composed key
        };

        final var autoIncremented = ids.size() == 1 && ids.get(0).getAnnotation(Id.class).autoIncremented();
        final var tableName = ofNullable(table.value())
                .map(this::escapeString)
                .orElse(className.substring(className.lastIndexOf('.') + 1));

        final var columns = Stream.concat(
                        ids.stream(),
                        standardColumns.stream())
                .collect(toMap(identity(), this::toSimpleColumn, (a, b) -> a, LinkedHashMap::new));
        final var columnsMapping = columns.values().stream()
                .flatMap(it -> it.javaName() == null ? it.columns().values().stream() : Stream.of(it))
                .collect(toMap(SimpleEntity.SimpleColumn::javaName, it -> escapeString(it.databaseName())));
        entities.put(className, new SimpleEntity(tableName, columns.values()));

        final var onInsertCounter = new AtomicInteger(1);
        final var onUpdateCounter = new AtomicInteger(1);

        final var insertInjections = toInjectionsOfFirstIfAny(onInsertCb);
        final var updateInjections = toInjectionsOfFirstIfAny(onUpdateCb);
        final var deleteInjections = toInjectionsOfFirstIfAny(onDeleteCb);
        final var loadInjections = toInjectionsOfFirstIfAny(onLoadCb);
        final boolean hasCallbackInjection = Stream.of(insertInjections, updateInjections, deleteInjections, loadInjections)
                .filter(Objects::nonNull)
                .mapToInt(List::size)
                .sum() > 0;

        return new Output(new GeneratedClass(packagePrefix + tableClassName,
                packageLine +
                        "\n" +
                        generationVersion() +
                        "public class " + tableClassName + " extends " + BaseEntity.class.getName() + "<" + className + ", " + idType + "> {\n" +
                        "    public " + tableClassName + "(final " + DatabaseConfiguration.class.getName() + " configuration" +
                        (hasCallbackInjection ? ", final " + RuntimeContainer.class.getName() + " main__container" : "") + ") {\n" +
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
                                        columns.entrySet().stream()
                                                .filter(it -> standardColumns.contains(it.getKey()))
                                                .flatMap(it -> it.getValue().javaName() == null ?
                                                        it.getValue().columns().entrySet().stream() :
                                                        Stream.of(it))
                                                .map(field -> newColumnMetadataStart(field.getKey()) + ")"))
                                .collect(joining(",\n", "", "\n")) +
                        "          ),\n" +
                        "          " + autoIncremented + ",\n" +
                        "          (" + (onInsertCb.isEmpty() ? "instance" : "entity") + ", statement) -> {\n" +
                        (!onInsertCb.isEmpty() ?
                                createInstanceFromCallback(onInsertCb.get(0), insertInjections, "entity", true) :
                                "") +
                        (autoIncremented ? standardColumns.stream() : Stream.concat(ids.stream(), standardColumns.stream()))
                                .flatMap(it -> {
                                    final var column = columns.get(it);
                                    if (column != null && column.columns() != null) { // if a nested column
                                        final var base = "instance." + column.embeddableJavaName() + "()";
                                        return column.columns().entrySet().stream().map(nested -> {
                                            final var value = base + "." + nested.getValue().javaName() + "()";
                                            return jdbcSetter(
                                                    nested.getKey(), onInsertCounter.getAndIncrement(),
                                                    base + " == null || " + value + " == null", value);
                                        });
                                    }
                                    return Stream.of(jdbcSetter(
                                            it, onInsertCounter.getAndIncrement(),
                                            "instance." + it.getSimpleName() + "()"));
                                })
                                .map(it -> "            " + it)
                                .collect(joining("\n", "", "\n")) +
                        "            return instance;\n" +
                        "          },\n" +
                        "          (" + (onUpdateCb.isEmpty() ? "instance" : "entity") + ", statement) -> {\n" +
                        (!onUpdateCb.isEmpty() ?
                                createInstanceFromCallback(onUpdateCb.get(0), updateInjections, "entity", true) :
                                "") +
                        columns.entrySet().stream()
                                .filter(it -> standardColumns.contains(it.getKey()))
                                .flatMap(it -> {
                                    if (it.getValue().columns() != null) {
                                        final var base = "instance." + it.getValue().embeddableJavaName() + "()";
                                        return it.getValue().columns().entrySet().stream().map(nested -> {
                                            final var value = base + "." + nested.getValue().javaName() + "()";
                                            return jdbcSetter(
                                                    nested.getKey(), onUpdateCounter.getAndIncrement(),
                                                    base + " == null || " + value + " == null", value);
                                        });
                                    }
                                    return Stream.of(jdbcSetter(
                                            it.getKey(), onUpdateCounter.getAndIncrement(),
                                            "instance." + it.getValue().javaName() + "()"));
                                })
                                .map(it -> "            " + it)
                                .collect(joining("\n", "", "\n")) +
                        ids.stream()
                                .map(it -> "            " + jdbcSetter(
                                        it, onUpdateCounter.getAndIncrement(),
                                        "instance." + it.getSimpleName() + "()"))
                                .collect(joining("\n", "", "\n")) +
                        "            return instance;\n" +
                        "          },\n" +
                        "          (instance, statement) -> {\n" +
                        (!onDeleteCb.isEmpty() ?
                                createInstanceFromCallback(onDeleteCb.get(0), deleteInjections, "instance", false) :
                                "") +
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
                        createFactory(constructorParameters, onLoadCb, loadInjections, columns, columnsMapping, "return", type) +
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
                                "      " + metadata(type) + ");\n" +
                                "  }\n" +
                                "\n" +
                                "  @Override\n" +
                                "  public " + tableClassName + " create(final " + RuntimeContainer.class.getName() + " container, final " +
                                List.class.getName() + "<" + Instance.class.getName() + "<?>> dependents) {\n" +
                                "    return new " + tableClassName + "(lookup(container, " + DatabaseConfiguration.class.getName() + ".class, dependents)" + (hasCallbackInjection ? ", container" : "") + ");\n" +
                                "  }\n" +
                                "}\n" +
                                "\n") :
                        null);
    }

    private String createInstanceFromCallback(final ExecutableElement callback, final List<Bean.FieldInjection> injections,
                                              final String varName, final boolean returns) {
        final var fnName = callback.getSimpleName().toString();
        if (injections == null || injections.isEmpty()) {
            return "            " + (returns ? "final var instance = " : "") + varName + "." + fnName + "();\n";
        }

        final var tryInstances = "try (" + injections.stream()
                .map(it -> "final var " + it.name() + " = " + eventLookup(it))
                .collect(joining(";\n         ")) + ") {\n";
        final var callbackCall = injections.stream()
                .map(Bean.FieldInjection::name)
                .map(n -> n + ".instance()")
                .collect(joining(", ", varName + "." + fnName + "(", ");\n"));
        if (!returns) {
            return (tryInstances +
                    "  " + callbackCall +
                    "}\n")
                    .indent(12);
        }
        return ("var instance = " + varName + ";\n" +
                tryInstances +
                "  instance = " + callbackCall +
                "}\n")
                .indent(12);
    }

    private List<Bean.FieldInjection> toInjectionsOfFirstIfAny(final List<ExecutableElement> onInsertCb) {
        return onInsertCb.stream()
                .map(i -> createExecutableFieldInjections(i.getParameters().stream().toList()).toList())
                .findFirst()
                .orElse(null);
    }

    private String createFactory(final Collection<? extends VariableElement> constructorParameters,
                                 final List<ExecutableElement> onLoadCb, final List<Bean.FieldInjection> loadInjections,
                                 final Map<VariableElement, SimpleEntity.SimpleColumn> columns, final Map<String, String> columnsMapping,
                                 final String resultPrefix, final TypeElement recordType) {
        return constructorParameters.stream()
                .map(p -> {
                    final var column = columns.get(p);
                    final var targetType = p.asType();
                    if (column != null && column.columns() != null) { // embeddable
                        return createFactory(
                                column.columns().keySet(), List.of(), loadInjections, column.columns(), columnsMapping,
                                "final var " + column.embeddableJavaName() + " = " +
                                        "(" + Function.class.getName() + "<" + ResultSet.class.getName() + ", " + ParsedType.of(targetType).className() + ">) ",
                                (TypeElement) processingEnv.getTypeUtils().asElement(targetType));
                    }
                    return createInstance(columnsMapping, p.getSimpleName(), targetType);
                })
                .collect(joining()) +
                "            " + resultPrefix + " rset -> {\n" +
                "              try {\n" +
                "                " + (onLoadCb.isEmpty() ? "return " : "final var entity = ") + "new " + recordType + "(" + constructorParameters.stream()
                .map(it -> it.getSimpleName().toString() + ".apply(rset)")
                .collect(joining(", ")) + ");\n" +
                (onLoadCb.isEmpty() ?
                        "" :
                        onLoadCallback(onLoadCb, loadInjections)) +
                "              } catch (final " + SQLException.class.getName() + " e) {\n" +
                "                throw new " + PersistenceException.class.getName() + "(e);\n" +
                "              }\n" +
                "            };\n";
    }

    private String onLoadCallback(final List<ExecutableElement> onLoadCb, final List<Bean.FieldInjection> loadInjections) {
        final var callback = onLoadCb.get(0);
        final boolean isVoid = callback.getReturnType() instanceof NoType;
        final var call = createInstanceFromCallback(callback, loadInjections, "entity", false);
        if (isVoid) {
            return "return " + call.stripLeading();
        }
        return "    " + call + "                return entity;\n";
    }

    private String createInstance(final Map<String, String> columnsMapping, final Name simpleName, final TypeMirror type) {
        final var javaName = simpleName.toString();
        final var leftSide = "            final var " + javaName + " = ";
        final var ofPart = "Of(columns.indexOf(\"" + columnsMapping.getOrDefault(javaName, javaName).toLowerCase(ROOT) + "\")";
        final var name = ParsedType.of(type).className();

        if (isEnum(type)) {
            return leftSide + "enum" + ofPart + ", " + name + ".class);\n";
        }

        return leftSide + columnReaderPrefix(type, name) + ofPart + switch (name) {
            case "int", "double", "float", "long", "boolean", "byte" -> ", true";
            case "java.lang.Integer", "java.lang.Double", "java.lang.Float", "java.lang.Long", "java.lang.Boolean", "java.lang.Byte" ->
                    ", false";
            default -> "";
        } + ");\n";
    }

    private List<? extends VariableElement> selectConstructorFor(final Supplier<String> clazz, final TypeElement type) {
        return findConstructors(type)
                .filter(e -> e.getParameters().size() >= 1)
                .max(RECORD_CONSTRUCTOR_COMPARATOR)
                .orElseThrow(() -> new IllegalArgumentException("@Table classes must have at least one argument constructor: " + clazz.get()))
                .getParameters();
    }

    private SimpleEntity.SimpleColumn toSimpleColumn(final VariableElement id) {
        final var simpleName = id.getSimpleName().toString();

        final var elt = processingEnv.getTypeUtils().asElement(id.asType());
        if (!(elt instanceof TypeElement te) || !ofNullable(elt)
                .map(it -> it.getAnnotation(Table.class))
                .map(it -> Objects.equals(Table.EMBEDDABLE, it.value()))
                .orElse(false)) {
            final var column = ofNullable(id.getAnnotation(Column.class))
                    .map(Column::name)
                    .map(this::escapeString)
                    .filter(it -> !it.isBlank() && !it.isEmpty());
            return new SimpleEntity.SimpleColumn(simpleName, column.orElse(simpleName), null, null);
        }

        return new SimpleEntity.SimpleColumn(
                null, null, simpleName,
                selectConstructorFor(() -> id.asType().toString(), te).stream()
                        .collect(toMap(identity(), this::toSimpleColumn, (a, b) -> a, LinkedHashMap::new)));
    }

    private String columnReaderPrefix(final TypeMirror typeMirror, final String name) {
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
            default -> throw new IllegalArgumentException("Unsupported type: " + typeMirror);
        };
    }

    private String jdbcGetter(final Element elt, final int jdbcIndex) {
        final var type = elt.asType();
        final boolean isEnum = isEnum(type);
        final var fqn = isEnum ? "java.lang.String" : ParsedType.of(type).className();
        return "rset.get" + jdbcMarker(type, fqn) + "(" + jdbcIndex + ")";
    }

    private String jdbcSetter(final Element elt, final int jdbcIndex, final String accessor) {
        return jdbcSetter(elt, jdbcIndex, accessor + " == null", accessor);
    }

    private String jdbcSetter(final Element elt, final int jdbcIndex, final String nullCheck, final String accessor) {
        final var type = elt.asType();
        final boolean isEnum = isEnum(type);
        final var fqn = isEnum ? "java.lang.String" : ParsedType.of(type).className();
        final var handleNull = !PRIMITIVES.contains(fqn);
        return (handleNull ?
                "if (" + nullCheck + ") { " +
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

    private boolean isEnum(final TypeMirror type) {
        return type instanceof DeclaredType dt && dt.asElement().getKind() == ElementKind.ENUM;
    }

    private void isNotPrivate(final ExecutableElement executableElement) {
        if (executableElement.getModifiers().contains(Modifier.PRIVATE)) {
            throw new IllegalArgumentException("" +
                    "Forbidden private element: " + executableElement.getEnclosingElement() + "." + executableElement + ", " +
                    "make it protected/package scope at least.");
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
                .filter(it -> !it.isBlank() && !it.isEmpty())
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
