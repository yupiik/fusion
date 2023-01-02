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
package test.p;


import io.yupiik.fusion.framework.api.event.Emitter;
import io.yupiik.fusion.framework.build.api.cli.Command;
import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;

import java.util.List;

public interface Commands {
    @Command(name = "c1", description = "A super command.")
    public static class C1 implements Runnable {
        private final Conf conf;
        private final Emitter aBean;

        public C1(final Conf conf, final Emitter aBean) {
            this.conf = conf;
            this.aBean = aBean;
        }

        @Override
        public void run() {
            System.setProperty(C1.class.getName(), "conf=" + conf + ", bean = " + (aBean != null));
        }

        @RootConfiguration("c1")
        public record Conf(@Property(documentation = "The main name.") String name, Nested nested,
                           List<Nested> nesteds,
                           List<String> list) {}

        public record Nested(String lower) {}
    }
}