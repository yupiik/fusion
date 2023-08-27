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
package io.yupiik.kubernetes.operator.base;

import io.yupiik.kubernetes.operator.base.test.MockController;
import io.yupiik.kubernetes.operator.base.test.OperatorSupport;
import io.yupiik.kubernetes.operator.base.test.sample.SampleOperator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@OperatorSupport
class SampleOperatorTest {
    @Test
    void handleEvents(final MockController controller) throws InterruptedException {
        final var logger = Logger.getLogger(SampleOperator.class.getName());
        final var captured = new CopyOnWriteArrayList<String>();
        final var latch = new CountDownLatch(2);
        final var handler = new Handler() {
            @Override
            public void publish(final LogRecord record) {
                captured.add(record.getMessage());
                latch.countDown();
            }

            @Override
            public void flush() {

            }

            @Override
            public void close() throws SecurityException {
                flush();
            }
        };
        logger.addHandler(handler);
        try {
            controller.sendEvent("""
                    {
                     "type":"ADDED",
                     "object": {
                       "kind": "Sample",
                       "apiVersion": "fusion.yupiik.io/v1",
                       "metadata": {
                         "name": "test1",
                         "namespace": "default"
                       },
                       "spec": {
                         "message": "hello"
                       }
                     }
                    }""");
            controller.sendEvent("""
                    {
                     "type":"DELETED",
                     "object": {
                       "kind": "Sample",
                       "apiVersion": "fusion.yupiik.io/v1",
                       "metadata": {
                         "name": "test1",
                         "namespace": "default"
                       },
                       "spec": {
                         "message": "hello"
                       }
                     }
                    }""");
            latch.await();
            assertEquals(
                    List.of(
                            "[ADD] SampleResource[metadata=Metadata[uid=null, name=test1, namespace=default, labels=null, annotations=null], spec=Spec[message=hello]]",
                            "[DELETE] SampleResource[metadata=Metadata[uid=null, name=test1, namespace=default, labels=null, annotations=null], spec=Spec[message=hello]]"
                    ),
                    captured);
        } finally {
            logger.removeHandler(handler);
        }
    }
}
