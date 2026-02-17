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

import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcError
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcRequest
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcResponse
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcTransformer
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcWriterBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

class JsonRpcOutOfOrderResponseTest {

    @Test
    fun testExecuteMatchesOutOfOrderResponsesById() = runBlockingUnit {
        val transformer = OutOfOrderResponseTransformer()
        val writer =
            JsonRpcWriterBase(
                scope = CoroutineScope(coroutineContext + SupervisorJob()),
                context = coroutineContext,
                env = ksrpcEnvironment { },
                comm = transformer
            )

        val first = async { writer.execute("first", JsonPrimitive("a"), isNotify = false) }
        val second = async { writer.execute("second", JsonPrimitive("b"), isNotify = false) }

        assertEquals(JsonPrimitive("first-result"), first.await())
        assertEquals(JsonPrimitive("second-result"), second.await())
        writer.close()
    }

    @Test
    fun testExecuteMatchesOutOfOrderMixedSuccessAndErrorById() = runBlockingUnit {
        val transformer = OutOfOrderMixedResponseTransformer()
        val writer =
            JsonRpcWriterBase(
                scope = CoroutineScope(coroutineContext + SupervisorJob()),
                context = coroutineContext,
                env = ksrpcEnvironment { },
                comm = transformer
            )

        val first =
            async { runCatching { writer.execute("first", JsonPrimitive("a"), isNotify = false) } }
        val second =
            async { runCatching { writer.execute("second", JsonPrimitive("b"), isNotify = false) } }

        val firstResult = first.await()
        val secondResult = second.await()
        assertTrue(firstResult.isFailure)
        assertTrue(firstResult.exceptionOrNull()?.message?.contains("first failed") == true)
        assertEquals(JsonPrimitive("second-result"), secondResult.getOrThrow())
        writer.close()
    }

    private class OutOfOrderResponseTransformer : JsonRpcTransformer() {
        private val firstRequest = CompletableDeferred<JsonRpcRequest>()
        private val secondRequest = CompletableDeferred<JsonRpcRequest>()
        private var emitted = 0

        override val isOpen: Boolean
            get() = emitted < 2

        override suspend fun send(message: JsonElement) {
            val request = Json.decodeFromJsonElement(JsonRpcRequest.serializer(), message)
            if (!firstRequest.isCompleted) {
                firstRequest.complete(request)
            } else if (!secondRequest.isCompleted) {
                secondRequest.complete(request)
            }
        }

        override suspend fun receive(): JsonElement? {
            val first = firstRequest.await()
            val second = secondRequest.await()
            val response =
                when (emitted++) {
                    0 ->
                        JsonRpcResponse(
                            result = JsonPrimitive("second-result"),
                            id = second.id
                        )

                    1 ->
                        JsonRpcResponse(
                            result = JsonPrimitive("first-result"),
                            id = first.id
                        )

                    else -> return null
                }
            return Json.encodeToJsonElement(JsonRpcResponse.serializer(), response)
        }

        override fun close(cause: Throwable?) {
            emitted = 2
        }
    }

    private class OutOfOrderMixedResponseTransformer : JsonRpcTransformer() {
        private val firstRequest = CompletableDeferred<JsonRpcRequest>()
        private val secondRequest = CompletableDeferred<JsonRpcRequest>()
        private var emitted = 0

        override val isOpen: Boolean
            get() = emitted < 2

        override suspend fun send(message: JsonElement) {
            val request = Json.decodeFromJsonElement(JsonRpcRequest.serializer(), message)
            if (!firstRequest.isCompleted) {
                firstRequest.complete(request)
            } else if (!secondRequest.isCompleted) {
                secondRequest.complete(request)
            }
        }

        override suspend fun receive(): JsonElement? {
            val first = firstRequest.await()
            val second = secondRequest.await()
            val response =
                when (emitted++) {
                    0 ->
                        JsonRpcResponse(
                            result = JsonPrimitive("second-result"),
                            id = second.id
                        )

                    1 ->
                        JsonRpcResponse(
                            error =
                                JsonRpcError(
                                    JsonRpcError.INTERNAL_ERROR,
                                    "first exploded",
                                    Json.encodeToJsonElement(
                                        RpcFailure.serializer(),
                                        RpcFailure("first failed")
                                    )
                                ),
                            id = first.id
                        )

                    else -> return null
                }
            return Json.encodeToJsonElement(JsonRpcResponse.serializer(), response)
        }

        override fun close(cause: Throwable?) {
            emitted = 2
        }
    }
}
