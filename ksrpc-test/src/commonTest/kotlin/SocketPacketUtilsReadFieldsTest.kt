/*
 * Copyright (C) 2026 Jason Monk <monkopedia@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.sockets.internal.readFields
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlin.test.Test
import kotlin.test.assertEquals

class SocketPacketUtilsReadFieldsTest {

    @Test
    fun testReadFieldsParsesHeaderLinesAndIgnoresInvalidOnes() = runBlockingUnit {
        val channel = ByteChannel(autoFlush = true)
        channel.writeStringUtf8("Content-Type: application/json\r\n")
        channel.writeStringUtf8("InvalidHeaderLine\r\n")
        channel.writeStringUtf8("X-Test: hello:world\r\n")
        channel.writeStringUtf8("\r\n")

        val fields = channel.readFields()

        assertEquals(
            mapOf(
                "Content-Type" to "application/json",
                "X-Test" to "hello:world"
            ),
            fields
        )
    }
}
