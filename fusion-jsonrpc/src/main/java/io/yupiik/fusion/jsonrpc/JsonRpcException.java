/*
 * Copyright (c) 2021-2022 - Yupiik SAS - https://www.yupiik.com
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
package io.yupiik.fusion.jsonrpc;

public class JsonRpcException extends RuntimeException {
    private final int code;
    private final String message;
    private final Object data;

    public JsonRpcException(final int code, final String message) {
        super(message);
        this.code = code;
        this.message = message;
        this.data = null;
    }

    public JsonRpcException(final int code, final String message, final Throwable parent) {
        this(code, message, null, parent);
    }

    public JsonRpcException(final int code, final String message, final Object data, final Throwable parent) {
        super(message, parent);
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public Object data() {
        return data;
    }

    public int code() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
