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
package io.yupiik.fusion.persistence.impl;

import io.yupiik.fusion.persistence.api.PersistenceException;
import io.yupiik.fusion.persistence.api.ResultSetWrapper;
import io.yupiik.fusion.persistence.api.SQLFunction;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;

import static java.util.Spliterator.IMMUTABLE;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

public class ResultSetWrapperImpl implements ResultSetWrapper {
    private final ResultSet resultSet;

    public ResultSetWrapperImpl(final ResultSet resultSet) {
        this.resultSet = resultSet;
    }

    @Override
    public boolean hasNext() {
        try {
            return resultSet.next();
        } catch (final SQLException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    public <T> List<T> mapAll(final SQLFunction<ResultSet, T> mapper) {
        return stream(spliteratorUnknownSize(new Iterator<T>() {
            @Override
            public boolean hasNext() {
                try {
                    return resultSet.next();
                } catch (final SQLException e) {
                    throw new PersistenceException(e);
                }
            }

            @Override
            public T next() {
                try {
                    return mapper.apply(resultSet);
                } catch (final SQLException e) {
                    throw new PersistenceException(e);
                }
            }
        }, IMMUTABLE), false).collect(toList());
    }

    @Override
    public <T> T map(final SQLFunction<ResultSet, T> mapper) {
        try {
            return mapper.apply(resultSet);
        } catch (final SQLException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    public ResultSet get() {
        return resultSet;
    }
}
