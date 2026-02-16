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

import com.monkopedia.ksrpc.channels.CallData
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CallDataAccessTest {

    @Test
    fun serializedCallDataCanBeReadAsSerializedOnly() {
        val callData = CallData.create("payload")

        assertFalse(callData.isBinary)
        assertEquals("payload", callData.readSerialized())
        assertFailsWith<IllegalStateException> {
            callData.readBinary()
        }
    }

    @Test
    fun binaryCallDataCanBeReadAsBinaryOnly() = runBlockingUnit {
        val callData = CallData.createBinary<String>(ByteReadChannel("payload".encodeToByteArray()))

        assertTrue(callData.isBinary)
        val bytes = callData.readBinary().readRemaining().readBytes()
        assertEquals("payload", bytes.decodeToString())
        assertFailsWith<IllegalStateException> {
            callData.readSerialized()
        }
    }
}
