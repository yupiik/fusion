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
package io.yupiik.fusion.json;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.Optional;

public interface JsonMapper extends AutoCloseable {
    <A> byte[] toBytes(A instance);

    <A> A fromBytes(Class<A> type, byte[] bytes);

    <A> A fromBytes(Type type, byte[] bytes);

    <A> A fromString(Class<A> type, String string);

    <A> A fromString(Type type, String string);

    <A> String toString(A instance);

    <A> void write(A instance, Writer writer);

    <A> A read(Type type, Reader rawReader);

    <A> A read(Class<A> type, Reader reader);

    @Override
    void close();

    /**
     * Enables to unwrap not first citizen features.
     *
     * @param type the expected type to enable.
     * @param <T>  the feature type.
     * @return an optional potentially filled with the requested feature.
     */
    default <T> Optional<T> as(final Class<T> type) {
        if (type.isInstance(this)) {
            return Optional.of(type.cast(this));
        }
        return Optional.empty();
    }

    /**
     * Enables to create a <b>new</b> {@link JsonMapper} with a slightly different tuning.
     * <p>
     * IMPORTANT: don't forget to close it too.
     */
    // @Experimental
    interface Configuring {
        /**
         * IMPORTANT: this method is a best effort and depends the generated codecs and type you pass to the mapper.
         * It returns a new mapper instance - thread safe - and does not change the underlying mapper behavior.
         *
         * @return enable the <b>hint</b> to try to keep null values in the serialization.
         */
        Configuring serializeNulls();

        /**
         * Creates a child builder of the parent one (the one you called {@link JsonMapper#as(Class)} on.
         * Ensure to call {@link JsonMapper#close()} on it when no more needed and that its scope is smaller or equals to the enclosing mapper.
         *
         * @return the {@link JsonMapper} respecting the configuration done.
         */
        JsonMapper build();
    }
}
