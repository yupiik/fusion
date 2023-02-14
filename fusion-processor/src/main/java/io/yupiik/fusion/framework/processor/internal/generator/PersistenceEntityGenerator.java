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
import io.yupiik.fusion.framework.build.api.persistence.Id;
import io.yupiik.fusion.framework.build.api.persistence.Table;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.meta.Docs;
import io.yupiik.fusion.persistence.api.Entity;
import io.yupiik.fusion.persistence.impl.DatabaseImpl;
import io.yupiik.fusion.persistence.spi.DatabaseTranslation;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;

public class PersistenceEntityGenerator extends BaseGenerator implements Supplier<PersistenceEntityGenerator.Output> {
    private static final String SUFFIX = "$FusionPersistenceEntity";

    private final Table table;
    private final boolean beanForPersistenceEntities;
    private final String packageName;
    private final String className;
    private final TypeElement type;
    private final Collection<Docs.ClassDoc> docs;

    public PersistenceEntityGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                                      final boolean beanForPersistenceEntities, final String packageName,
                                      final String className, final Table table, final TypeElement type,
                                      final Collection<Docs.ClassDoc> docs) {
        super(processingEnv, elements);
        this.table = table;
        this.beanForPersistenceEntities = beanForPersistenceEntities;
        this.packageName = packageName;
        this.className = className;
        this.type = type;
        this.docs = docs;
    }

    @Override
    public Output get() {
        final var packagePrefix = packageName == null || packageName.isBlank() ? "" : (packageName + '.');
        final var packageLine = packagePrefix.isBlank() ? "" : ("package " + packageName + ";\n\n");
        final var tableClassName = className + SUFFIX;
        final var constructorParameters = findConstructors(type)
                .filter(e -> e.getParameters().size() >= 1)
                .max(comparing(e -> {
                    if (e.getModifiers().contains(PUBLIC)) {
                        return 1000;
                    }
                    if (e.getModifiers().contains(PROTECTED)) {
                        return 100;
                    }
                    if (!e.getModifiers().contains(PRIVATE)) { // package scope
                        return 10;
                    }
                    return 0; // private
                }))
                .orElseThrow(() -> new IllegalArgumentException("@Table classes must have at least one argument constructor with a @RootConfiguration parameter: " + packagePrefix + className))
                .getParameters();

        if (constructorParameters.size() > 1 && !beanForPersistenceEntities) {
            throw new IllegalArgumentException("@Table classes can only get injections if using a bean so ensure to set -Afusion.generateBeanForPersistenceEntities in your compiler: " + packagePrefix + className);
        }

        final var hasInjections = beanForPersistenceEntities && constructorParameters.size() != 1;


        return new Output(new GeneratedClass(packagePrefix + tableClassName,
                        packageLine +
                                "import "+ Entity.class.getName() + ";\n" +
                                "import "+ DatabaseImpl.class.getName() + ";\n" +
                                "\n" +
                                "import " + PreparedStatement.class.getName() + ";\n" +
                                "import " + ResultSet.class.getName() + ";\n" +
                                "import " + Collection.class.getName() + ";\n" +
                                "import " + List.class.getName() + ";\n" +
                                "import " + Map.class.getName() + ";\n" +
                                "import " + Objects.class.getName() + ";\n" +
                                "import " + Function.class.getName() + ";\n" +
                                "\n" +
                                "import static " + Collectors.class.getName() + ".joining;\n" +
                                "import static " + Collectors.class.getName() + ".toList;\n" +
                                "\n" +
                                generationVersion() +
                                "public class " + tableClassName + " implements " + Entity.class.getName() + "<" + className + "> {\n" +
                                "   private final DatabaseImpl database;\n" +
                                "\n" +
                                "    // table definition\n" +
                                "    private final String table;\n" +
                                "    // " + type.getRecordComponents().stream().map(String::valueOf).collect(joining(", ")) + "\n" +
                                "    private final Map<String, Entity.ColumnModel> fields;\n" +
                                "    private final List<Entity.IdColumnModel> idFields;\n" +
                                "    private final Collection<Entity.ColumnModel> insertFields;\n" +
                                "\n" +
                                "    // pre-built queries\n" +
                                "    private final String findByIdQuery;\n" +
                                "    private final String updateQuery;\n" +
                                "    private final String deleteQuery;\n" +
                                "    private final String insertQuery;\n" +
                                "    private final String findAllQuery;\n" +
                                "    private final boolean autoIncremented;\n" +
                                "\n" +
                                "    public " + tableClassName + "(" + DatabaseImpl.class.getName() + " database, Class<" + className + "> type, " + DatabaseTranslation.class.getName() + " translation) {\n" +
                                "        this.database = database;\n" +
                                "        this.table = database.getTranslation().wrapTableName(\"" + table.value() + "\");\n" +
                                "        this.autoIncremented = true;\n" +
                                "\n" +
                                "        this.fields = Map.of(" + type.getRecordComponents().stream()
                                .map(e -> "\"" + e.getSimpleName() + "\", new Entity.ColumnModel(\"" + e.getSimpleName() + "\", " + e.asType().toString().substring(e.asType().toString().lastIndexOf(".")+1, e.asType().toString().length()) + ".class, false, null)").collect(joining(",\n                ")) + ");\n" +
                                "        this.idFields = List.of(" + type.getRecordComponents().stream().filter(e -> !Objects.isNull(e.getAnnotation(Id.class)))
                                .map(e -> "new Entity.IdColumnModel(\"" + e.getSimpleName() + "\", " + e.asType().toString().substring(e.asType().toString().lastIndexOf(".")+1, e.asType().toString().length()) + ".class, null, Objects.hash(\"" + e.getSimpleName() + "\"), true)").collect(joining(",\n                ")) + ");\n" +
                                "\n" +
                                "        if (autoIncremented) {\n" +
                                "            this.insertFields = fields.values().stream()\n" +
                                "                    .filter(it -> it.field != idFields.get(0).field)\n" +
                                "                    .collect(toList());\n" +
                                "        } else {\n" +
                                "            this.insertFields = fields.values();\n" +
                                "        }\n" +
                                "\n" +
                                "        final var byIdWhereClause = \" WHERE \" + idFields.stream()\n" +
                                "                .map(f -> database.getTranslation().wrapFieldName(f.field) + \" = ?\")\n" +
                                "                .collect(joining(\" AND \"));\n" +
                                "\n" +
                                "        final var fieldNamesCommaSeparated = fields.values().stream()\n" +
                                "                .map(f -> database.getTranslation().wrapFieldName(f.field))\n" +
                                "                .collect(joining(\", \"));\n" +
                                "\n" +
                                "        final var insertFieldsCommaSeparated = autoIncremented ?\n" +
                                "                fields.values().stream()\n" +
                                "                        .filter(it -> !Objects.equals(it.field, idFields.get(0).field))\n" +
                                "                        .map(f -> database.getTranslation().wrapFieldName(f.field))\n" +
                                "                        .collect(joining(\", \")) :\n" +
                                "                fieldNamesCommaSeparated;\n" +
                                "\n" +
                                "        this.findByIdQuery = \"\" +\n" +
                                "                \"SELECT \" +\n" +
                                "                fieldNamesCommaSeparated +\n" +
                                "                \" FROM \" + table +\n" +
                                "                byIdWhereClause;\n" +
                                "\n" +
                                "        this.updateQuery = \"\" +\n" +
                                "                \"UPDATE \" + table + \" SET \" +\n" +
                                "                fields.values().stream().map(f -> database.getTranslation().wrapFieldName(f.field) + \" = ?\").collect(joining(\", \")) +\n" +
                                "                byIdWhereClause;\n" +
                                "\n" +
                                "        this.deleteQuery = \"\" +\n" +
                                "                \"DELETE FROM \" + table + byIdWhereClause;\n" +
                                "\n" +
                                "        this.insertQuery = \"\" +\n" +
                                "                \"INSERT INTO \" + table + \" (\" + insertFieldsCommaSeparated + \") \" +\n" +
                                "                \"VALUES (\" + insertFields.stream()\n" +
                                "                .map(f -> \"?\")\n" +
                                "                .collect(joining(\", \")) + \")\";\n" +
                                "\n" +
                                "        this.findAllQuery = \"\" +\n" +
                                "                \"SELECT \" + fieldNamesCommaSeparated +\n" +
                                "                \" FROM \" + table;\n" +
                                "    }" +
                                "\n" +
                                "    @Override\n" +
                                "    public String[] ddl() {\n" +
                                "        return new String[0];\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public Class<?> getRootType() {\n" +
                                "        return this.getClass();\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public String getTable() {\n" +
                                "        return this.table;\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public String getFindByIdQuery() {\n" +
                                "        return this.findByIdQuery;\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public String getFindByAllQuery() {\n" +
                                "        return this.findAllQuery;\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public String getUpdateQuery() {\n" +
                                "        return this.updateQuery;\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public String getDeleteQuery() {\n" +
                                "        return this.deleteQuery;\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public String getInsertQuery() {\n" +
                                "        return this.insertQuery;\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public String getFindAllQuery() {\n" +
                                "        return this.findAllQuery;\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public List<ColumnMetadata> getOrderedColumns() {\n" +
                                "        return null;\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public boolean isAutoIncremented() {\n" +
                                "        return this.autoIncremented;\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public void onInsert(Object instance, PreparedStatement statement) {\n" +
                                "\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public void onDelete(Object instance, PreparedStatement statement) {\n" +
                                "\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public void onUpdate(Object instance, PreparedStatement statement) {\n" +
                                "\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public void onFindById(PreparedStatement stmt, Object id) {\n" +
                                "\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public CustomerEntity onAfterInsert(Object instance, PreparedStatement statement) {\n" +
                                "        return null;\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public String concatenateColumns(ColumnsConcatenationRequest request) {\n" +
                                "        return null;\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public Function mapFromPrefix(String prefix, ResultSet resultSet) {\n" +
                                "        return null;\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public Function mapFromPrefix(String prefix, String... columnNames) {\n" +
                                "        return null;\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public Function<ResultSet, CustomerEntity> nextProvider(String[] columns, ResultSet rset) {\n" +
                                "        return null;\n" +
                                "    }\n" +
                                "\n" +
                                "    @Override\n" +
                                "    public Function<ResultSet, CustomerEntity> nextProvider(ResultSet resultSet) {\n" +
                                "        return null;\n" +
                                "    }" +
                                "}\n" +
                                "\n"),
                beanForPersistenceEntities ?
                        new GeneratedClass(packagePrefix + tableClassName + '$' + FusionBean.class.getSimpleName(), packageLine +
                                generationVersion() +
                                "public class " + tableClassName + '$' + FusionBean.class.getSimpleName() + " extends " + BaseBean.class.getName() + "<" + tableClassName + "> {\n" +
                                "  public " + tableClassName + '$' + FusionBean.class.getSimpleName() + "() {\n" +
                                "    super(\n" +
                                "      " + tableClassName + ".class,\n" +
                                "      " + findScope(type) + ".class,\n" +
                                "      " + findPriority(type) + ",\n" +
                                "      " + Map.class.getName() + ".of());\n" +
                                "  }\n" +
                                "\n" +
                                "  @Override\n" +
                                "  public " + tableClassName + " create(final " + RuntimeContainer.class.getName() + " container, final " +
                                List.class.getName() + "<" + Instance.class.getName() + "<?>> dependents) {\n" +
                                "    return new " + tableClassName + "(null, " + className + ".class, null);\n" +
                                "  }\n" +
                                "}\n" +
                                "\n") :
                        null);
    }

    public record Output(BaseGenerator.GeneratedClass entity, BaseGenerator.GeneratedClass bean) {
    }
}
