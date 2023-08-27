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
package io.yupiik.kubernetes.operator.base.impl;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.scanning.Bean;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class Beans {
    @Bean
    @ApplicationScoped // wrapped to enable proper cleanup on shutdown
    public Threads threads() {
        return new Threads(Executors.newScheduledThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()), new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(final Runnable r) {
                return new Thread(r, "io.yupiik.fusion.kubernetes.operator-" + counter.incrementAndGet());
            }
        }));
    }

    @Bean
    @ApplicationScoped // just easier to use in the app
    public ScheduledExecutorService simpleThreads(final Threads threads) {
        return threads.executor();
    }

    @Bean
    @ApplicationScoped
    public KubernetesClient kubernetesClient(final OperatorConfiguration configuration,
                                             final ScheduledExecutorService executor) {
        return new KubernetesClient(new io.yupiik.fusion.kubernetes.client.KubernetesClientConfiguration()
                .setClientCustomizer(b -> b
                        .connectTimeout(Duration.ofMinutes(1))
                        .executor(executor))
                .setMaster(configuration.kubernetes().master())
                .setToken(configuration.kubernetes().token())
                .setCertificates(configuration.kubernetes().certificates())
                .setSkipTls(configuration.kubernetes().skipTls()));
    }
}
