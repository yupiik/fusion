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
package io.yupiik.fusion.jsonrpc.impl.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.json.internal.JsonStrings;
import io.yupiik.fusion.json.internal.codec.BaseJsonCodec;
import io.yupiik.fusion.json.serialization.JsonCodec;
import io.yupiik.fusion.jsonrpc.Response;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

public class ResponseJsonCodecBean extends BaseBean<ResponseJsonCodecBean.Impl> {
    public ResponseJsonCodecBean() {
        super(Impl.class, DefaultScoped.class, 1000, Map.of());
    }

    @Override
    public Impl create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        return new Impl();
    }

    public static class Impl extends BaseJsonCodec<Response> {
        public Impl() {
            super(Response.class);
        }

        @Override
        public Response read(final DeserializationContext context) throws IOException {
            throw new UnsupportedEncodingException("response is not supposed to be read");
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void write(final Response value, final SerializationContext ctx) throws IOException {
            boolean first = true;
            final var out = ctx.writer();
            out.write('{');
            if (value.jsonrpc() != null) {
                first = false;
                out.write("\"jsonrpc\":");
                out.write(JsonStrings.escape(value.jsonrpc()));
            }
            if (value.id() != null) {
                if (!first) {
                    out.write(',');
                } else {
                    first = false;
                }
                out.write("\"id\":");
                out.write(JsonStrings.escape(value.id()));
            }
            if (value.result() != null) {
                if (!first) {
                    out.write(',');
                } else {
                    first = false;
                }
                out.write("\"result\":");

                JsonCodec jsonCodec = ctx.codec(value.result().getClass());
                if (jsonCodec == null) {
                    jsonCodec = ctx.codec(Object.class);
                }
                jsonCodec.write(value.result(), ctx);
            }
            if (value.error() != null) {
                if (!first) {
                    out.write(',');
                }
                out.write("\"error\":");
                ctx.codec(Response.ErrorResponse.class).write(value.error(), ctx);
            }
            out.write('}');
        }
    }
}
