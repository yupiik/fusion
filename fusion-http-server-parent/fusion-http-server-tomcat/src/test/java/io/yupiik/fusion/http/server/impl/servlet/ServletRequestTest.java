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
package io.yupiik.fusion.http.server.impl.servlet;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServletRequestTest {
    @Test
    void caseInsensitiveHeaders() {
        final var connector = new Connector();
        final var req = new Request(connector, new org.apache.coyote.Request());
        req.getCoyoteRequest().getMimeHeaders().addValue("SImpLE").setString("a");
        final var wrapper = new ServletRequest(req);
        assertEquals("a", wrapper.header("simple"));
        assertEquals("a", wrapper.header("Simple"));
        assertEquals("a", wrapper.header("SimPle"));

        // same for the map
        assertEquals(List.of("a"), wrapper.headers().get("simple"));
        assertEquals(List.of("a"), wrapper.headers().get("SimPlE"));
        assertEquals(List.of("a"), wrapper.headers().get("Simple"));
    }
}
