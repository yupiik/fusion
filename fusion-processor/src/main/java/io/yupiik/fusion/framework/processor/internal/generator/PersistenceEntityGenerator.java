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

import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.framework.api.container.bean.FunctionalBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.build.api.persistence.Column;
import io.yupiik.fusion.framework.build.api.persistence.Id;
import io.yupiik.fusion.framework.build.api.persistence.Table;
import io.yupiik.fusion.framework.processor.internal.Bean;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.ParsedType;
import io.yupiik.fusion.framework.processor.internal.metadata.MetadataContributorRegistry;
import io.yupiik.fusion.framework.processor.internal.persistence.SimpleEntity;
import io.yupiik.fusion.persistence.api.Entity;
import io.yupiik.fusion.persistence.impl.ColumnMetadataImpl;
import io.yupiik.fusion.persistence.impl.DatabaseConfiguration;
import io.yupiik.fusion.persistence.impl.FunctionalEntity;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.yupiik.fusion.framework.processor.internal.stream.Streams.withIndex;
import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

public class PersistenceEntityGenerator extends BaseGenerator implements Supplier<PersistenceEntityGenerator.Output> {
    private static final Set<String> PRIMITIVES = Set.of("int", "boolean", "char", "short", "byte", "long", "double", "float");

    private static final String SUFFIX = "$FusionPersistenceEntity";

    private final Table table;
    private final boolean beanForPersistenceEntities;
    private final String packageName;
    private final String className;
    private final String binaryClassName;
    private final TypeElement type;
    private final TypeMirror onDelete;
    private final TypeMirror onInsert;
    private final TypeMirror onLoad;
    private final TypeMirror onUpdate;
    private final Map<String, SimpleEntity> entities;
    private final Set<String> methodParameterNames;

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
        final var sourceName = type.getQualifiedName().toString();
        this.className = packageName == null || packageName.isBlank() ? sourceName : sourceName.substring(packageName.length() + 1);
        this.binaryClassName = className;
        this.type = type;
        this.onDelete = onDelete;
        this.onInsert = onInsert;
        this.onLoad = onLoad;
        this.onUpdate = onUpdate;
        this.entities = entities;
        this.methodParameterNames = processingEnv.getElementUtils().getAllMembers(type).stream()
                .filter(ExecutableElement.class::isInstance).map(ExecutableElement.class::cast)
                .flatMap(it -> it.getParameters().stream()).map(it -> it.getSimpleName().toString()).collect(toSet());
    }

    @Override
    // todo: we parse too often parameter types, make it a sorted map or something like that + support byte[] and other java/sql bindings (bigdecimal, biginteger, java.time)
    public Output get() {
        final var packagePrefix = packageName == null || packageName.isBlank() ? "" : (packageName + '.');
        final var tableClassName = binaryClassName + SUFFIX;
        final var constructorParameters = selectConstructorFor(() -> packagePrefix + className, type);

        // todo: avoid to visit so often the methods and do a single visitor to capture them all?
        // note: in practise not critical for a record (few methods) but better

        final var entityCallbacks = elements.findAnnotatedMethods(type, onInsert, onUpdate, onDelete, onLoad);
        final var onInsertCb = entityCallbacks.get(onInsert).stream().peek(this::isNotPrivate).toList();
        if (onInsertCb.size() > 1) {
            throw new IllegalArgumentException("Multiple @OnInsert were found, this behavior is forbidden for now because not deterministic.");
        }

        final var onUpdateCb = entityCallbacks.get(onUpdate).stream().peek(this::isNotPrivate).toList();
        if (onUpdateCb.size() > 1) {
            throw new IllegalArgumentException("Multiple @OnUpdate were found, this behavior is forbidden for now because not deterministic.");
        }

        final var onDeleteCb = entityCallbacks.get(onDelete).stream().peek(this::isNotPrivate).toList();
        if (onDeleteCb.size() > 1) {
            throw new IllegalArgumentException("Multiple @OnDelete were found, this behavior is forbidden for now because not deterministic.");
        }

        final var onLoadCb = entityCallbacks.get(onLoad).stream().peek(this::isNotPrivate).toList();
        if (onLoadCb.size() > 1) {
            throw new IllegalArgumentException("Multiple @OnLoad were found, this behavior is forbidden for now because not deterministic.");
        }

        final var ids = constructorParameters.stream()
                .filter(it -> it.getAnnotation(Id.class) != null)
                .toList();
        final var idsSet = Set.copyOf(ids); // parameters are unique instances so fast lookups are accurate
        final var standardColumns = constructorParameters.stream()
                .filter(it -> !idsSet.contains(it)) // @Column is optional
                .toList();

        final var idType = switch (ids.size()) {
            case 1 -> boxed(ParsedType.of(ids.get(0).asType()).className());
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
        final var stableId = packagePrefix + tableClassName;
        final var entityType = Entity.class.getName() + "<" + className + ", " + idType + ">";
        final var factoryType = Entity.class.getName() + ".Factory<" + className + ", " + idType + ">";
        final var operations = new HashSet<Integer>(List.of(5, 15, 16));
        if (!onInsertCb.isEmpty() || !(autoIncremented ? standardColumns : constructorParameters).isEmpty()) {
            operations.add(10);
        }
        if (!onUpdateCb.isEmpty() || !constructorParameters.isEmpty()) {
            operations.add(11);
        }
        if (!onDeleteCb.isEmpty() || !ids.isEmpty()) {
            operations.add(12);
        }
        if (!ids.isEmpty()) {
            operations.add(13);
        }
        if (autoIncremented) {
            operations.add(14);
        }
        final var methods = new ArrayList<Binding.Method>();
        methods.add(new Binding.Method(5,
                "private static " + entityType + " $method(" + DatabaseConfiguration.class.getName() + " configuration, " +
                        RuntimeContainer.class.getName() + " main__container) {\n" +
                        "return new " + FunctionalEntity.class.getName() + "<>(new " + FunctionalEntity.class.getName() + ".Context(configuration, main__container), " + className + ".class, \"" + tableName + "\", java.util.List.of(\n" +
                        Stream.concat(
                                        withIndex(ids.stream())
                                                .map(id -> newColumnMetadataStart(id.item()) + ", " +
                                                        id.index() + ", " +
                                                        id.item().getAnnotation(Id.class).autoIncremented() +
                                                        ")"),
                                        columns.entrySet().stream()
                                                .filter(it -> !idsSet.contains(it.getKey()))
                                                .flatMap(it -> it.getValue().javaName() == null ?
                                                        it.getValue().columns().entrySet().stream() :
                                                        Stream.of(it))
                                                .map(field -> newColumnMetadataStart(field.getKey()) + ")"))
                                .collect(joining(",\n", "", "\n")) +
                        "), " + autoIncremented + ", " +
                        Stream.of(10, 11, 12, 13, 14, 15, 16).map(op -> operations.contains(op) ? Binding.helperReference(op) : "null").collect(joining(", ")) +
                        ");\n}\n",
                ""));
        methods.add(sqlMethod(10, className, className + " " + (onInsertCb.isEmpty() ? "instance" : "entity"),
                "java.sql.PreparedStatement statement",
                (!onInsertCb.isEmpty() ?
                                createInstanceFromCallback(onInsertCb.get(0), insertInjections, "entity", true) :
                                "") +
                        (autoIncremented ? standardColumns.stream() : Stream.concat(ids.stream(), standardColumns.stream()))
                                .flatMap(it -> {
                                    final var column = columns.get(it);
                                    if (column != null && column.columns() != null) { // if a nested column
                                        return embeddedSetters(column, onInsertCounter);
                                    }
                                    return Stream.of(jdbcSetter(
                                            it, onInsertCounter.getAndIncrement(),
                                            "instance." + it.getSimpleName() + "()"));
                                })
                                .map(it -> "            " + it)
                                .collect(joining("\n", "", "\n")) + "return instance;\n"));
        methods.add(sqlMethod(11, className, className + " " + (onUpdateCb.isEmpty() ? "instance" : "entity"),
                "java.sql.PreparedStatement statement",
                (!onUpdateCb.isEmpty() ?
                                createInstanceFromCallback(onUpdateCb.get(0), updateInjections, "entity", true) :
                                "") +
                        columns.entrySet().stream()
                                .filter(it -> !idsSet.contains(it.getKey()))
                                .flatMap(it -> {
                                    if (it.getValue().columns() != null) {
                                        return embeddedSetters(it.getValue(), onUpdateCounter);
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
                                .collect(joining("\n", "", "\n")) + "return instance;\n"));
        methods.add(sqlMethod(12, "void", className + " instance", "java.sql.PreparedStatement statement",
                (!onDeleteCb.isEmpty() ?
                                createInstanceFromCallback(onDeleteCb.get(0), deleteInjections, "instance", false) :
                                "") +
                        withIndex(ids.stream())
                                .map(it -> "            " + jdbcSetter(
                                        it.item(), it.index() + 1,
                                        "instance." + it.item().getSimpleName() + "()"))
                                .collect(joining("\n", "", "\n"))));
        methods.add(sqlMethod(13, "void", idType + " id", "java.sql.PreparedStatement statement",
                switch (ids.size()) {
                            case 1 -> "            " + jdbcSetter(ids.get(0), 1, "id") + "\n";
                            // multiple ids, we get a list and we bind each item
                            default -> "            final var it = id.iterator();\n" +
                                    withIndex(ids.stream())
                                            .map(it -> {
                                                final var value = "id" + it.index();
                                                return "            final var " + value + " = (" +
                                                        ParsedType.of(it.item().asType()).className() + ") it.next();\n" +
                                                        "            " + jdbcSetter(it.item(), it.index() + 1, value);
                                            })
                                            .collect(joining("\n", "", "\n"));
                        }));
        if (autoIncremented) {
            methods.add(sqlMethod(14, className, className + " entity", "java.sql.ResultSet rset",
                "return new " + className + "(" + constructorParameters.stream()
                        .map(it -> idsSet.contains(it) ? jdbcGetter(it, 1) : "entity." + it.getSimpleName() + "()")
                        .collect(joining(", ")) + ");\n"));
        }
        final var readers = new ArrayList<String>();
        final var row = rowExpression(constructorParameters, columns, columnsMapping, type, readers);
        methods.add(sqlMethod(15, "io.yupiik.fusion.persistence.api.SQLFunction<java.sql.ResultSet, ?>[]",
                "java.util.List<String> columns", null,
                readers.stream().collect(joining(",\n", "return new io.yupiik.fusion.persistence.api.SQLFunction[]{\n", "};\n"))));
        methods.add(sqlMethod(16, className, "io.yupiik.fusion.persistence.api.SQLFunction<java.sql.ResultSet, ?>[] readers",
                "java.sql.ResultSet rset",
                (onLoadCb.isEmpty() ? "return " : "final var entity = ") + row + ";\n" +
                        (onLoadCb.isEmpty() ? "" : createInstanceFromCallback(onLoadCb.get(0), loadInjections, "entity", false) + "return entity;\n")));
        final var artifacts = new ArrayList<Binding.Artifact>();
        artifacts.add(new Binding.Artifact(stableId, new Binding(packageName, null, factoryType,
                Binding.helperReference(5),
                methods.stream().filter(it -> operations.contains(it.operation())).toList())));
        final var beanId = stableId + "." + FusionBean.class.getSimpleName();
        if (beanForPersistenceEntities) {
            final var beanType = "new " + Types.ParameterizedTypeImpl.class.getCanonicalName() + "(" + Entity.class.getName() + ".class, " +
                    className + ".class, " + (ids.size() == 1 ? idType + ".class" :
                    "new " + Types.ParameterizedTypeImpl.class.getCanonicalName() + "(java.util.List.class, Object.class)") + ")";
            artifacts.add(new Binding.Artifact(beanId, new Binding(packageName, "bean", FusionBean.class.getName() + "<?>",
                    "new " + FunctionalBean.class.getName() + "<>(" + beanType + ", " + DefaultScoped.class.getName() +
                            ".class, " + findPriority(type) + ", 1000, " + metadata(type) + ", " + Binding.helperReference(0) + ", null, null, null, false)",
                    List.of(new Binding.Method(0,
                            "@SuppressWarnings(\"cast\")\nprivate static " + entityType + " $method(" + RuntimeContainer.class.getName() + " container, java.util.List<io.yupiik.fusion.framework.api.Instance<?>> dependents) {\n" +
                                    "return " + Binding.reference(stableId) + ".create(lookup(container, " + DatabaseConfiguration.class.getName() + ".class, dependents), container);\n}\n", "")))));
        }
        return new Output(new GeneratedClass(stableId, null, artifacts), beanForPersistenceEntities ? beanId : null);
    }

    private Binding.Method sqlMethod(final int operation, final String resultType, final String first,
                                     final String second, final String body) {
        final boolean configuration = body.contains("configuration.");
        final boolean container = body.contains("main__container");
        final var context = sqlArgument("context");
        return new Binding.Method(operation,
                "private static " + ("void".equals(resultType) ? "Void" : resultType) + " $method(" +
                        FunctionalEntity.class.getName() + ".Context " + context + ", final " + first + ", final " +
                        (second == null ? "Void " + sqlArgument("ignored") : second) + ") throws java.sql.SQLException {\n" +
                        (configuration ? "final var configuration = " + context + ".configuration();\n" : "") +
                        (container ? "final var main__container = " + context + ".container();\n" : "") + body +
                        ("void".equals(resultType) ? "return null;\n" : "") + "}\n", "");
    }

    private String sqlArgument(final String role) {
        var name = "$" + role;
        while (methodParameterNames.contains(name)) {
            name += "_";
        }
        return name;
    }

    private Stream<String> embeddedSetters(final SimpleEntity.SimpleColumn column, final AtomicInteger counter) {
        final var base = "embedded" + counter.get();
        return Stream.concat(Stream.of("final var " + base + " = instance." + column.embeddableJavaName() + "();"),
                column.columns().entrySet().stream().map(nested -> jdbcSetter(nested.getKey(), counter.getAndIncrement(),
                        base + " == null ? null : " + base + "." + nested.getValue().javaName() + "()", true)));
    }

    private String rowExpression(final Collection<? extends VariableElement> parameters,
                                 final Map<VariableElement, SimpleEntity.SimpleColumn> columns,
                                 final Map<String, String> columnsMapping, final TypeElement recordType,
                                 final List<String> readers) {
        return parameters.stream().map(parameter -> {
            final var column = columns.get(parameter);
            if (column != null && column.columns() != null) {
                return rowExpression(column.columns().keySet(), column.columns(), columnsMapping,
                        (TypeElement) processingEnv.getTypeUtils().asElement(parameter.asType()), readers);
            }
            final int index = readers.size();
            readers.add(reader(columnsMapping, parameter.getSimpleName().toString(), parameter.asType()));
            final var parsed = ParsedType.of(parameter.asType());
            final var target = parsed.type() == ParsedType.Type.PARAMETERIZED_TYPE ?
                    parsed.raw() + parsed.args().stream().collect(joining(", ", "<", ">")) : boxed(parsed.className());
            return "(" + target + ") readers[" + index + "].apply(rset)";
        }).collect(joining(", ", "new " + recordType + "(", ")"));
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

    private String reader(final Map<String, String> columnsMapping, final String simpleName, final TypeMirror type) {
        final var javaName = simpleName;
        final var leftSide = FunctionalEntity.class.getName() + ".";
        final var ofPart = "Of(columns.indexOf(\"" + columnsMapping.getOrDefault(javaName, javaName).toLowerCase(ROOT) + "\")";
        final var parsedType = ParsedType.of(type);
        final var name = switch (parsedType.type()) {
            case PARAMETERIZED_TYPE -> parsedType.raw() + parsedType.args().stream().collect(joining(", ", "<", ">"));
            default -> parsedType.className();
        };

        if (isEnum(type)) {
            return leftSide + "enum" + ofPart + ", " + name + ".class)";
        }

        final var prefix = columnReaderPrefix(name);
        if (!"object".equals(prefix)) {
            return leftSide + prefix + ofPart + switch (name) {
                case "int", "double", "float", "long", "boolean", "byte", "short" -> ", true";
                case "java.lang.Integer", "java.lang.Double", "java.lang.Float", "java.lang.Long", "java.lang.Boolean",
                     "java.lang.Byte", "java.lang.Short" -> ", false";
                default -> "";
            } + ")";
        }

        // no native accessor: route the read through the translation to let dialects adapt it
        return "configuration.getTranslation().reader(columns.indexOf(\"" +
                columnsMapping.getOrDefault(javaName, javaName).toLowerCase(ROOT) + "\"), " +
                (switch (parsedType.type()) {
                    case PARAMETERIZED_TYPE -> parsedType.raw();
                    default -> parsedType.className();
                }) + ".class)";
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

    private String columnReaderPrefix(final String name) {
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
            // java.math.BigInteger, java.time types and unknown types have no native accessor:
            // they are routed through DatabaseTranslation#reader (default "object" case)
            default -> "object";
        };
    }

    private String jdbcGetter(final Element elt, final int jdbcIndex) {
        final var type = elt.asType();
        final boolean isEnum = isEnum(type);
        final var fqn = isEnum ? "java.lang.String" : ParsedType.of(type).className();
        return "rset.get" + jdbcMarker(type, fqn) + "(" + jdbcIndex + ")";
    }

    private String jdbcSetter(final Element elt, final int jdbcIndex, final String accessor) {
        return jdbcSetter(elt, jdbcIndex, accessor, false);
    }

    private String jdbcSetter(final Element elt, final int jdbcIndex, final String accessor, final boolean nullable) {
        final var type = elt.asType();
        final boolean isEnum = isEnum(type);
        final var parsedType = ParsedType.of(type);
        var fqn = isEnum ? "java.lang.String" : parsedType.className();
        if (fqn == null) {
            if (parsedType.type() == ParsedType.Type.PARAMETERIZED_TYPE && !parsedType.args().isEmpty()) {
                fqn = parsedType.raw() + parsedType.args().stream().collect(joining(", ", "<", ">"));
            } else {
                throw new IllegalArgumentException("Unsupported type: " + type);
            }
        }
        if ("Object".equals(jdbcMarker(type, fqn))) {
            // Translated bindings, including nulls, remain configuration-specific.
            final var classLiteral = (parsedType.type() == ParsedType.Type.PARAMETERIZED_TYPE ?
                    parsedType.raw() : fqn) + ".class";
            return "configuration.getTranslation().bind(statement, " + jdbcIndex + ", " + accessor + ", " + classLiteral + ");";
        }
        return nullable || !PRIMITIVES.contains(fqn) ?
                FunctionalEntity.class.getName() + ".bind" + (isEnum ? "Enum" : jdbcMarker(type, fqn)) +
                        "(statement, " + jdbcIndex + ", " + accessor + ");" :
                "statement.set" + jdbcMarker(type, fqn) + "(" + jdbcIndex + ", " + accessor + ");";
    }

    // generics require boxed types (BaseEntity<A, B> with B = id type)
    private String boxed(final String className) {
        return switch (className) {
            case "boolean" -> "java.lang.Boolean";
            case "byte" -> "java.lang.Byte";
            case "short" -> "java.lang.Short";
            case "int" -> "java.lang.Integer";
            case "long" -> "java.lang.Long";
            case "float" -> "java.lang.Float";
            case "double" -> "java.lang.Double";
            case "char" -> "java.lang.Character";
            default -> className;
        };
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
            default -> "Object";
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
        final var parsedType = ParsedType.of(element.asType());
        return "            " +
                "new " + ColumnMetadataImpl.class.getName() + "(" +
                "\"" + element.getSimpleName().toString() + "\", " +
                (switch (parsedType.type()) {
                    case PARAMETERIZED_TYPE -> parsedType.createParameterizedTypeImpl();
                    default -> parsedType.className() + ".class";
                }) + ", " +
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

    public record Output(GeneratedClass entity, String beanClassName) {
    }
}
