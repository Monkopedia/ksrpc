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

import com.monkopedia.ksrpc.binary.kxio.SourceTransformer
import com.monkopedia.ksrpc.binary.kxio.asRpcBinaryData
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.RpcCallId
import com.monkopedia.ksrpc.channels.SerializedService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

private class SourceTransformerTestService(override val env: KsrpcEnvironment<String>) :
    SerializedService<String> {
    override suspend fun call(
        endpoint: String,
        input: CallData<String>,
        callId: RpcCallId?
    ): CallData<String> = input

    override suspend fun close() = Unit

    override suspend fun onClose(onClose: suspend () -> Unit) = Unit
}

class SourceBinaryTransformerTest {
    @Test
    fun sourceTransformerRoundTripsBuffer() = runBlockingUnit {
        val env = ksrpcEnvironment { }
        val service = SourceTransformerTestService(env)
        val payload = "Hello kotlinx.io".encodeToByteArray()
        val buffer = Buffer().apply { write(payload, 0, payload.size) }

        val transformed = SourceTransformer.transform(buffer, service)
        val untransformed = SourceTransformer.untransform(transformed, service)
        assertEquals(payload.decodeToString(), untransformed.readByteArray().decodeToString())
    }

    @Test
    fun asRpcBinaryDataExposesSourceBytes() = runBlockingUnit {
        val payload = "Streaming".encodeToByteArray()
        val src = Buffer().apply { write(payload, 0, payload.size) }
        val collected = mutableListOf<Byte>()
        src.asRpcBinaryData().transferTo { bytes, off, len ->
            for (i in 0 until len) collected += bytes[off + i]
        }
        assertEquals(payload.toList(), collected)
    }
}
