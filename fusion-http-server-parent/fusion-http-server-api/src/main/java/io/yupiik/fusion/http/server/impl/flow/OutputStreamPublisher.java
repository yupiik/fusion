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
package io.yupiik.fusion.http.server.impl.flow;

import io.yupiik.fusion.http.server.api.IOConsumer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

// enables to write the response body directly on the (servlet) output stream,
// bypassing any char conversion - the preferred path for byte oriented payloads like JSON
public class OutputStreamPublisher implements Flow.Publisher<ByteBuffer> {
    private final IOConsumer<OutputStream> delegate;

    public OutputStreamPublisher(final IOConsumer<OutputStream> delegate) {
        this.delegate = delegate;
    }

    // optimization path
    public IOConsumer<OutputStream> getDelegate() {
        return delegate;
    }

    @Override
    public void subscribe(final Flow.Subscriber<? super ByteBuffer> subscriber) { // shouldn't be used but impl for compat
        final var buffer = new ByteArrayOutputStream();
        try {
            delegate.accept(buffer);
        } catch (final IOException e) {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(final long n) {
                    // no-op
                }

                @Override
                public void cancel() {
                    // no-op
                }
            });
            subscriber.onError(e);
            return;
        }
        new BytesPublisher(buffer.toByteArray()).subscribe(subscriber);
    }
}
