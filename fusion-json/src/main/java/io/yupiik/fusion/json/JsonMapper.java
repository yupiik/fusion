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

    @Override
    void close();
}
