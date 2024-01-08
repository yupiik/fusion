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
package io.yupiik.fusion.httpclient.core.response;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;

public class StaticResponseInfo implements HttpResponse.ResponseInfo {
    private final int statusCode;
    private final HttpHeaders headers;
    private final HttpClient.Version version;

    public StaticResponseInfo(final int statusCode, final HttpHeaders headers, final HttpClient.Version version) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.version = version;
    }

    @Override
    public int statusCode() {
        return statusCode;
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public HttpClient.Version version() {
        return version;
    }
}
