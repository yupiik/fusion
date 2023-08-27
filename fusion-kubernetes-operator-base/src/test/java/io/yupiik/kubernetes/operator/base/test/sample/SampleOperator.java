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
package io.yupiik.kubernetes.operator.base.test.sample;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import io.yupiik.kubernetes.operator.base.spi.Operator;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

@ApplicationScoped
public class SampleOperator extends Operator.Base<SampleResource> {
    private final Logger logger = Logger.getLogger(getClass().getName());

    private final KubernetesClient kubernetes;
    private final JsonMapper jsonMapper;

    public SampleOperator(final KubernetesClient client, final JsonMapper jsonMapper) {
        super(
                SampleResource.class,
                new DefaultOperatorConfiguration(true, List.of("default"), "samples", "fusion.yupiik.io/v1"));
        this.kubernetes = client;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public CompletionStage<?> onStart() {
        logger.info(() -> "Starting operator " + getClass().getName());
        return super.onStart();
    }

    @Override
    public void onAdd(final SampleResource object) {
        logger.info(() -> "[ADD] " + object);
    }

    @Override
    public void onDelete(final SampleResource object) {
        logger.info(() -> "[DELETE] " + object);
    }

    @Override
    public void onModify(final SampleResource object) {
        logger.info(() -> "[MODIFY] " + object);
    }
}
