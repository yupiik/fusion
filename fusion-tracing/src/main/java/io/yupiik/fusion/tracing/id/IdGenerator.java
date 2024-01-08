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
package io.yupiik.fusion.tracing.id;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class IdGenerator implements Supplier<Object> {
    private final Supplier<Object> delegate;

    public IdGenerator(final Type type) {
        switch (type) {
            case COUNTER:
                delegate = new Supplier<>() {
                    private final AtomicLong counter = new AtomicLong();

                    @Override
                    public Object get() {
                        return counter.incrementAndGet();
                    }
                };
                break;
            case UUID:
                delegate = () -> UUID.randomUUID().toString();
                break;
            case HEX: // limited to 16 for the length cause of zipkin
            default:
                delegate = new Supplier<>() {
                    private final Random random = new Random(System.nanoTime());
                    private final char[] hexDigits = "0123456789abcdef".toCharArray();

                    @Override
                    public Object get() {
                        final StringBuilder sb = new StringBuilder(16);
                        for (int i = 0; i < 16; i++) {
                            sb.append(hexDigits[random.nextInt(16)]);
                        }
                        return sb.toString();
                    }
                };
        }
    }

    @Override
    public Object get() {
        return delegate.get();
    }

    public enum Type {
        COUNTER, UUID, HEX
    }
}
