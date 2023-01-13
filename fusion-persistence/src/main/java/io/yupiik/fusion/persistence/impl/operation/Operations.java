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
package io.yupiik.fusion.persistence.impl.operation;

import io.yupiik.fusion.framework.build.api.persistence.Operation;
import io.yupiik.fusion.framework.build.api.persistence.Statement;
import io.yupiik.fusion.persistence.api.Database;
import io.yupiik.fusion.persistence.api.Entity;
import io.yupiik.fusion.persistence.api.PersistenceException;
import io.yupiik.fusion.persistence.api.ResultSetWrapper;
import io.yupiik.fusion.persistence.api.SQLFunction;
import io.yupiik.fusion.persistence.api.StatementBinder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class Operations implements InvocationHandler {
    private static final Execution FAIL = args -> {
        throw new UnsupportedOperationException("Unknown operation");
    };

    private final Database database;
    private final Map<Method, Execution> operations;

    public <M> Operations(final Database database, final Class<M> api, final ClassLoader loader) {
        this.database = database;

        final Map<String, Entity<?>> aliases = ofNullable(api.getAnnotation(Operation.class))
                .map(Operation::aliases)
                .map(a -> new HashMap<String, Entity<?>>(Stream.of(a)
                        .collect(toMap(Operation.Alias::alias, it -> database.getOrCreateEntity(it.type())))))
                .orElseGet(HashMap::new); // we can enrich it for fqn so ensure it is writable
        this.operations = Stream.of(api.getMethods())
                .filter(m -> m.isAnnotationPresent(Statement.class))
                .collect(toMap(identity(), m -> prepare(aliases, loader, m)));
    }

    private Execution prepare(final Map<String, Entity<?>> aliases, final ClassLoader loader, final Method method) {
        final var rawStmt = method.getAnnotation(Statement.class).value();

        final var placeholderParameters = rawStmt.contains("${parameters#");
        final var parameterNames = placeholderParameters ?
                Stream.of(method.getParameters()).map(Parameter::getName).collect(toList()) :
                List.of();

        final var bindings = placeholderParameters ?
                new ArrayList<BiConsumer<Object[], StatementBinder>>() :
                List.<BiConsumer<Object[], StatementBinder>>of();
        final var sql = new Substitutor(key -> {
            final int sep = key.indexOf('#');
            if (sep < 0) {
                throw new IllegalArgumentException("Invalid placeholder: '" + key + "', expected '<alias>#fields', '<alias>#table', 'parameters#<name>'");
            }

            final var prefix = key.substring(0, sep).strip();
            final var suffix = key.substring(sep + 1).strip();

            if ("parameters".equals(prefix)) {
                final int subSep = suffix.indexOf('#');
                if (subSep > 0) {
                    final var paramName = suffix.substring(0, subSep);
                    final var bindingType = suffix.substring(subSep + 1);
                    final int index = parameterNames.indexOf(paramName);
                    if (index < 0) {
                        throw new IllegalArgumentException("Invalid parameter binding: '" + suffix + "' in " + method);
                    }
                    switch (bindingType) {
                        case "in":
                            bindings.add((a, b) -> {
                                final var value = a[index];
                                if (value == null) {
                                    return;
                                }
                                final var collection = (Collection<?>) value;
                                collection.forEach(b::bind);
                            });
                            return args -> {
                                final var value = args[index];
                                if (value == null) {
                                    return "1 <> 1"; // false, todo: move to translation?
                                }
                                final var collection = (Collection<?>) value;
                                return collection.stream()
                                        .map(i -> "?")
                                        .collect(joining(",", "in (", ")"));
                            };
                        default:
                            throw new IllegalArgumentException("Unsupported placeholder: '" + key + "' in " + method);
                    }
                }
                final int index = parameterNames.indexOf(suffix);
                if (index < 0) {
                    throw new IllegalArgumentException("Invalid parameter binding: '" + suffix + "' in " + method);
                }
                final var type = method.getParameterTypes()[index];
                bindings.add((a, b) -> b.bind(type, a[index]));
                return new Constant("?");
            }

            final var entity = aliases.computeIfAbsent(prefix, k -> {
                try {
                    return database.getOrCreateEntity(loader.loadClass(k));
                } catch (final ClassNotFoundException e) {
                    throw new IllegalArgumentException(e);
                }
            });
            switch (suffix.toLowerCase(ROOT)) {
                case "table":
                    return new Constant(entity.getTable());
                case "fields":
                    return new Constant(entity.concatenateColumns(new Entity.ColumnsConcatenationRequest()));
                default:
                    throw new IllegalArgumentException("Unknown attribute of entity: '" + key + "'");
            }
        }).replace(rawStmt);

        final BiConsumer<Object[], StatementBinder> binder;
        if (method.getParameterCount() == 0) {
            binder = (a, b) -> {
            };
        } else if (placeholderParameters) {
            binder = (args, b) -> bindings.forEach(i -> i.accept(args, b));
        } else {
            final var types = method.getParameterTypes();
            binder = (args, b) -> IntStream.range(0, args.length).forEach(i -> b.bind(types[i], args[i]));
        }

        if (!rawStmt.toLowerCase(ROOT).strip().startsWith("select")) {
            if (sql instanceof Constant) {
                final var sqlValue = Constant.class.cast(sql).apply(null);
                return args -> database.execute(sqlValue, b -> binder.accept(args, b));
            }
            return args -> database.execute(sql.apply(args), b -> binder.accept(args, b));
        }

        final Function<ResultSetWrapper, Object> outputMapper;
        final var returnType = method.getGenericReturnType();
        if (void.class == returnType) {
            outputMapper = rset -> null;
        } else if (long.class == returnType) {
            outputMapper = rset -> mapFirst(rset, r -> r.getLong(1));
        } else if (int.class == returnType) {
            outputMapper = rset -> mapFirst(rset, r -> r.getInt(1));
        } else if (boolean.class == returnType) {
            outputMapper = rset -> mapFirst(rset, r -> r.getBoolean(1));
        } else if (float.class == returnType) {
            outputMapper = rset -> mapFirst(rset, r -> r.getFloat(1));
        } else if (double.class == returnType) {
            outputMapper = rset -> mapFirst(rset, r -> r.getDouble(1));
        } else if (Number.class == returnType) {
            outputMapper = rset -> mapFirst(rset, r -> r.getObject(1));
        } else if (ParameterizedType.class.isInstance(returnType) &&
                Collection.class.isAssignableFrom(Class.class.cast(ParameterizedType.class.cast(returnType).getRawType()))) {
            outputMapper = rset -> database.mapAll(Class.class.cast(ParameterizedType.class.cast(returnType).getActualTypeArguments()[0]), rset.get());
        } else { // single line
            final var type = method.getReturnType();
            outputMapper = rset -> mapFirst(rset, r -> database.mapOne(type, r));
        }

        if (sql instanceof Constant) {
            final var sqlValue = Constant.class.cast(sql).apply(null);
            return args -> database.query(sqlValue, b -> binder.accept(args, b), outputMapper);
        }
        return args -> database.query(sql.apply(args), b -> binder.accept(args, b), outputMapper);
    }

    private Object mapFirst(final ResultSetWrapper rset, final SQLFunction<ResultSet, Object> mapper) {
        if (!rset.hasNext()) {
            throw new PersistenceException("No result found");
        }
        try {
            final var res = mapper.apply(rset.get());
            if (rset.hasNext()) {
                throw new PersistenceException("Ambiguous entity fetched!");
            }
            return res;
        } catch (final SQLException sqlex) {
            throw new PersistenceException(sqlex);
        }
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        if (Object.class == method.getDeclaringClass()) {
            switch (method.getName()) {
                case "equals":
                    return args.length == 1 && doEquals(proxy, args[0]);
                case "hashCode":
                    return super.hashCode();
                case "toString":
                    return "Operations[" + operations + "]";
                default: {
                    try {
                        return method.invoke(this, args);
                    } catch (final InvocationTargetException ite) {
                        throw ite.getTargetException();
                    }
                }
            }
        }
        return operations.getOrDefault(method, FAIL).execute(args);
    }

    private boolean doEquals(final Object proxy, final Object arg) { // it is a reference equals for the handler
        return arg == proxy || (arg != null && Proxy.isProxyClass(arg.getClass()) && super.equals(Proxy.getInvocationHandler(arg)));
    }

    private interface Execution {
        Object execute(Object[] args);
    }

    // from commons-text
    private static class Substitutor {
        private final Function<String, Function<Object[], String>> valueMap;

        private Substitutor(final Function<String, Function<Object[], String>> valueMap) {
            this.valueMap = valueMap;
        }

        public Function<Object[], String> replace(final String source) {
            if (source == null) {
                return a -> null;
            }
            final var parts = new ArrayList<Function<Object[], String>>();
            int offset = 0;
            do {
                final var start = source.indexOf("${", offset);
                if (start < 0) {
                    parts.add(new Constant(source.substring(offset)));
                    break;
                }
                final var end = source.indexOf("}", start);
                if (end < 0) {
                    parts.add(new Constant(source.substring(offset)));
                    break;
                }
                final var interpolation = valueMap.apply(source.substring(start + "${".length(), end));
                if (start > 0) {
                    parts.add(new Constant(source.substring(offset, start)));
                }
                parts.add(interpolation);
                offset = end + 1;
            } while (offset < source.length());
            final var optimized = optimize(parts);
            if (optimized.size() == 1) {
                return optimized.get(0);
            }
            return args -> optimized.stream().map(it -> it.apply(args)).collect(joining());
        }

        private List<Function<Object[], String>> optimize(final List<Function<Object[], String>> parts) {
            final var optimized = new ArrayList<Function<Object[], String>>(parts.size());
            final var buffer = new ArrayList<Constant>(parts.size());
            for (final var part : parts) {
                if (part instanceof Constant) {
                    buffer.add((Constant) part);
                } else {
                    if (!buffer.isEmpty()) {
                        optimized.add(new Constant(buffer.stream().map(c -> c.apply(null)).collect(joining())));
                        buffer.clear();
                    }
                    optimized.add(part);
                }
            }
            if (!buffer.isEmpty()) {
                optimized.add(new Constant(buffer.stream().map(c -> c.apply(null)).collect(joining())));
                buffer.clear();
            }
            return optimized;
        }
    }

    private static class Constant implements Function<Object[], String> {
        private final String value;

        private Constant(final String value) {
            this.value = value;
        }

        @Override
        public String apply(final Object[] args) {
            return value;
        }
    }
}
