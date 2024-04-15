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

    /**
     * IMPORTANT: this method is a best effort and depends the generated codecs and type you pass to the mapper.
     * It returns a new mapper instance - thread safe - and does not change the underlying mapper behavior.
     *
     * @return enable the <b>hint</b> to try to keep null values in the serialization.
     */
    // @Experimental
    default JsonMapper serializeNulls() {
        return this;
    }

    @Override
    void close();
}
