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
package io.yupiik.fusion.json.internal.parser;

import io.yupiik.fusion.json.JsonException;
import io.yupiik.fusion.json.spi.Parser;

import java.math.BigDecimal;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/** Applies document validation independently of the parser factory and codec used. */
public class ValidatingParser implements DocumentParser {
    private final Parser delegate;
    private final int maxDepth;
    // A null frame denotes an array; each object owns its decoded names.
    // No tracking allocation or key materialization is needed when validation is disabled.
    private final List<Set<String>> scopes;
    private boolean replay;
    private boolean started;
    private boolean complete;
    private int depth;

    public ValidatingParser(final Parser delegate, final boolean rejectDuplicateKeys) {
        this(delegate, rejectDuplicateKeys, 0);
    }

    public ValidatingParser(final Parser delegate, final boolean rejectDuplicateKeys, final int maxDepth) {
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth must be non-negative");
        }
        this.delegate = delegate;
        this.maxDepth = maxDepth;
        this.scopes = rejectDuplicateKeys ? new ArrayList<>() : null;
    }

    @Override
    public boolean hasNext() {
        return delegate.hasNext();
    }

    @Override
    public void rewind(final Event event) {
        delegate.rewind(event);
        replay = true;
    }

    @Override
    public Event next() {
        final var event = delegate.next();
        if (replay) {
            // The underlying cursor and validation state already include this event.
            replay = false;
            return event;
        }
        if (complete) {
            throw new JsonException("Unexpected token after the JSON root: " + event);
        }
        started = true;
        switch (event) {
            case START_OBJECT, START_ARRAY -> {
                if (maxDepth > 0 && depth >= maxDepth) {
                    throw new JsonException("JSON nesting depth exceeds maxDepth=" + maxDepth);
                }
                depth++;
            }
            case END_OBJECT, END_ARRAY -> {
                if (depth == 0) {
                    throw new JsonException("Unexpected closing token: " + event);
                }
                complete = --depth == 0;
            }
            case KEY_NAME -> {
                if (depth == 0) {
                    throw new JsonException("A JSON root cannot be a member name");
                }
            }
            default -> complete = depth == 0;
        }
        if (scopes != null) {
            switch (event) {
                case START_OBJECT -> scopes.add(new HashSet<>());
                case START_ARRAY -> scopes.add(null);
                case END_OBJECT, END_ARRAY -> scopes.remove(scopes.size() - 1);
                case KEY_NAME -> {
                    final var key = delegate.getString();
                    if (!scopes.get(scopes.size() - 1).add(key)) {
                        throw new JsonException("Duplicate JSON object key: " + key);
                    }
                }
                default -> {
                    // Values do not affect object name tracking.
                }
            }
        }
        return event;
    }

    @Override
    public void ensureDocument(final boolean rejectTrailingTokens) {
        if (!started || !complete || depth != 0 || replay) {
            throw new JsonException("The codec did not consume a complete JSON root");
        }
        if (rejectTrailingTokens && hasNext()) {
            throw new JsonException("Unexpected content after the JSON root");
        }
    }

    @Override
    public void enforceNext(final Event event) {
        if (!hasNext()) {
            throw new JsonException("Expected " + event + " stream is finished.");
        }
        final var next = next();
        if (next != event) {
            throw new JsonException("Expected " + event + " but got " + next);
        }
    }

    @Override
    public void skipObject() {
        if (isInObject()) {
            skip(Event.START_OBJECT, Event.END_OBJECT);
        }
    }

    @Override
    public void skipArray() {
        if (isInArray()) {
            skip(Event.START_ARRAY, Event.END_ARRAY);
        }
    }

    private void skip(final Event start, final Event end) {
        // The opening token has already been consumed, just like the core parser's skips.
        int level = 1;
        do {
            final var event = next();
            if (event == start) {
                level++;
            } else if (event == end) {
                level--;
            }
        } while (level > 0 && hasNext());
    }

    @Override
    public String getString() {
        return delegate.getString();
    }

    @Override
    public int matchString(final char[][] candidates, final IntUnaryOperator lengthOffsets) {
        return delegate.matchString(candidates, lengthOffsets);
    }

    @Override
    public CharBuffer getChars() {
        return delegate.getChars();
    }

    @Override
    public boolean isInArray() {
        return delegate.isInArray();
    }

    @Override
    public boolean isInObject() {
        return delegate.isInObject();
    }

    @Override
    public int getInt() {
        return delegate.getInt();
    }

    @Override
    public long getLong() {
        return delegate.getLong();
    }

    @Override
    public double getDouble() {
        return delegate.getDouble();
    }

    @Override
    public BigDecimal getBigDecimal() {
        return delegate.getBigDecimal();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
