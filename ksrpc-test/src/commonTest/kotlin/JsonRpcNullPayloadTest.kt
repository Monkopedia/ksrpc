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

import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcRequest
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcResponse
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcTransformer
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcWriterBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class JsonRpcNullPayloadTest {

    @Test
    fun testExecuteSupportsNullParamsAndNullResult() = runBlockingUnit {
        val transformer = NullPayloadTransformer()
        val writer =
            JsonRpcWriterBase(
                scope = CoroutineScope(coroutineContext + SupervisorJob()),
                context = coroutineContext,
                env = ksrpcEnvironment { },
                comm = transformer
            )

        val response = writer.execute("null-io", message = null, isNotify = false)
        assertEquals(null, response)

        val request = withTimeout(2_000) { transformer.sentRequest.await() }
        assertEquals("null-io", request.method)
        assertEquals(null, request.params)
        assertNotNull(request.id)
        writer.close()
    }

    private class NullPayloadTransformer : JsonRpcTransformer() {
        val sentRequest = CompletableDeferred<JsonRpcRequest>()
        private var emitted = false

        override val isOpen: Boolean
            get() = !emitted

        override suspend fun send(message: JsonElement) {
            if (!sentRequest.isCompleted) {
                sentRequest.complete(Json.decodeFromJsonElement(JsonRpcRequest.serializer(), message))
            }
        }

        override suspend fun receive(): JsonElement? {
            if (emitted) return null
            val request = sentRequest.await()
            emitted = true
            return Json.encodeToJsonElement(
                JsonRpcResponse.serializer(),
                JsonRpcResponse(
                    result = null,
                    id = request.id
                )
            )
        }

        override fun close(cause: Throwable?) {
            emitted = true
        }
    }
}
