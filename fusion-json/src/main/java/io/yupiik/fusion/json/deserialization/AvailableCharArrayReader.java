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
package io.yupiik.fusion.json.deserialization;

import java.io.CharArrayReader;

// enables to give to fusion JsonParser directly a char array used as input
// it will make parsing faster in all cases it applies
public class AvailableCharArrayReader extends CharArrayReader {
    public AvailableCharArrayReader(final char[] buf) {
        super(buf);
    }

    public char[] charArray() {
        return buf;
    }
}
