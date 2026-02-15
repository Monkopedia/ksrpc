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
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcError
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcRequest
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcResponse
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcTransformer
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcWriterBase
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

class JsonRpcErrorEnvelopeTest {

    @Test
    fun testWriterSendsInternalErrorEnvelopeWhenServiceThrows() = runBlockingUnit {
        val request =
            Json.encodeToJsonElement(
                JsonRpcRequest.serializer(),
                JsonRpcRequest(
                    method = "explode",
                    params = JsonPrimitive("input"),
                    id = JsonPrimitive(77)
                )
            )
        val transformer = QueuedTransformer(request)
        val writer =
            JsonRpcWriterBase(
                scope = CoroutineScope(coroutineContext + SupervisorJob()),
                context = coroutineContext,
                env = ksrpcEnvironment { },
                comm = transformer
            )

        writer.registerDefault(
            object : SerializedService<String> {
                override val env = ksrpcEnvironment { }
                override val context: CoroutineContext = EmptyCoroutineContext

                override suspend fun call(endpoint: String, input: CallData<String>): CallData<String> {
                    throw IllegalStateException("boom")
                }

                override suspend fun close() {}

                override suspend fun onClose(onClose: suspend () -> Unit) {}
            }
        )

        val sent = withTimeout(2_000) { transformer.firstSent.await() }
        val response = Json.decodeFromJsonElement(JsonRpcResponse.serializer(), sent)
        val error = assertNotNull(response.error)

        assertEquals(JsonRpcError.INTERNAL_ERROR, error.code)
        assertTrue(error.message.contains("boom"))
        assertEquals(JsonPrimitive(77), response.id)

        writer.close()
    }

    @Test
    fun testExecuteThrowsWithJsonRpcErrorCodeAndMessage() = runBlockingUnit {
        val transformer = RequestAwareErrorTransformer()
        val writer =
            JsonRpcWriterBase(
                scope = CoroutineScope(coroutineContext + SupervisorJob()),
                context = coroutineContext,
                env = ksrpcEnvironment { },
                comm = transformer
            )

        val thrown =
            assertFailsWith<IllegalStateException> {
                writer.execute("explode", JsonPrimitive("input"), isNotify = false)
            }

        assertTrue(thrown.message?.contains("JsonRpcError(-32603): server exploded") == true)
        writer.close()
    }

    @Test
    fun testExecuteReturnsJsonRpcResultOnSuccess() = runBlockingUnit {
        val transformer = RequestAwareSuccessTransformer(result = JsonPrimitive("ok"))
        val writer =
            JsonRpcWriterBase(
                scope = CoroutineScope(coroutineContext + SupervisorJob()),
                context = coroutineContext,
                env = ksrpcEnvironment { },
                comm = transformer
            )

        val response = writer.execute("ping", JsonPrimitive("input"), isNotify = false)
        assertEquals(JsonPrimitive("ok"), response)

        val request = withTimeout(2_000) { transformer.sentRequest() }
        assertEquals("ping", request.method)
        assertEquals(JsonPrimitive("input"), request.params)
        assertNotNull(request.id)
        writer.close()
    }

    @Test
    fun testExecuteNotifyDoesNotWaitForResponse() = runBlockingUnit {
        val transformer = NotifyTransformer()
        val writer =
            JsonRpcWriterBase(
                scope = CoroutineScope(coroutineContext + SupervisorJob()),
                context = coroutineContext,
                env = ksrpcEnvironment { },
                comm = transformer
            )

        val response = writer.execute("notify", JsonPrimitive("input"), isNotify = true)
        assertEquals(null, response)
        assertFalse(transformer.receiveCalled)
        writer.close()
    }

    @Test
    fun testWriterNotifyFailureDoesNotSendErrorEnvelope() = runBlockingUnit {
        val request =
            Json.encodeToJsonElement(
                JsonRpcRequest.serializer(),
                JsonRpcRequest(
                    method = "explode",
                    params = JsonPrimitive("input"),
                    id = null
                )
            )
        val transformer = QueuedTransformer(request)
        val callSeen = CompletableDeferred<Unit>()
        val writer =
            JsonRpcWriterBase(
                scope = CoroutineScope(coroutineContext + SupervisorJob()),
                context = coroutineContext,
                env = ksrpcEnvironment { },
                comm = transformer
            )

        writer.registerDefault(
            object : SerializedService<String> {
                override val env = ksrpcEnvironment { }
                override val context: CoroutineContext = EmptyCoroutineContext

                override suspend fun call(endpoint: String, input: CallData<String>): CallData<String> {
                    callSeen.complete(Unit)
                    throw IllegalStateException("boom notify")
                }

                override suspend fun close() {}

                override suspend fun onClose(onClose: suspend () -> Unit) {}
            }
        )

        withTimeout(2_000) { callSeen.await() }
        assertEquals(0, transformer.sendCount)
        writer.close()
    }

    @Test
    fun testWriterNotifySuccessDoesNotSendResponseEnvelope() = runBlockingUnit {
        val request =
            Json.encodeToJsonElement(
                JsonRpcRequest.serializer(),
                JsonRpcRequest(
                    method = "notify-success",
                    params = JsonPrimitive("input"),
                    id = null
                )
            )
        val transformer = QueuedTransformer(request)
        val callSeen = CompletableDeferred<Unit>()
        val writer =
            JsonRpcWriterBase(
                scope = CoroutineScope(coroutineContext + SupervisorJob()),
                context = coroutineContext,
                env = ksrpcEnvironment { },
                comm = transformer
            )

        writer.registerDefault(
            object : SerializedService<String> {
                override val env = ksrpcEnvironment { }
                override val context: CoroutineContext = EmptyCoroutineContext

                override suspend fun call(endpoint: String, input: CallData<String>): CallData<String> {
                    callSeen.complete(Unit)
                    return input
                }

                override suspend fun close() {}

                override suspend fun onClose(onClose: suspend () -> Unit) {}
            }
        )

        withTimeout(2_000) { callSeen.await() }
        assertEquals(0, transformer.sendCount)
        writer.close()
    }

    private class QueuedTransformer(vararg incoming: JsonElement) : JsonRpcTransformer() {
        private val queue = ArrayDeque(incoming.toList())
        val firstSent = CompletableDeferred<JsonElement>()
        var sendCount = 0

        override val isOpen: Boolean
            get() = queue.isNotEmpty()

        override suspend fun send(message: JsonElement) {
            sendCount++
            if (!firstSent.isCompleted) {
                firstSent.complete(message)
            }
        }

        override suspend fun receive(): JsonElement? = queue.removeFirstOrNull()

        override fun close(cause: Throwable?) {}
    }

    private class RequestAwareErrorTransformer(
        private val errorMessage: String = "server exploded",
        private val errorData: JsonElement? = null
    ) : JsonRpcTransformer() {
        private val request = CompletableDeferred<JsonRpcRequest>()
        private var emitted = false

        override val isOpen: Boolean
            get() = !emitted

        override suspend fun send(message: JsonElement) {
            if (!request.isCompleted) {
                request.complete(Json.decodeFromJsonElement(JsonRpcRequest.serializer(), message))
            }
        }

        override suspend fun receive(): JsonElement? {
            if (emitted) return null
            val req = request.await()
            emitted = true
            return Json.encodeToJsonElement(
                JsonRpcResponse.serializer(),
                JsonRpcResponse(
                    error = JsonRpcError(JsonRpcError.INTERNAL_ERROR, errorMessage, errorData),
                    id = req.id
                )
            )
        }

        override fun close(cause: Throwable?) {
            emitted = true
        }
    }

    private class RequestAwareSuccessTransformer(
        private val result: JsonElement = JsonPrimitive("ok")
    ) : JsonRpcTransformer() {
        private val request = CompletableDeferred<JsonRpcRequest>()
        private var emitted = false

        override val isOpen: Boolean
            get() = !emitted

        override suspend fun send(message: JsonElement) {
            if (!request.isCompleted) {
                request.complete(Json.decodeFromJsonElement(JsonRpcRequest.serializer(), message))
            }
        }

        suspend fun sentRequest(): JsonRpcRequest = request.await()

        override suspend fun receive(): JsonElement? {
            if (emitted) return null
            val req = request.await()
            emitted = true
            return Json.encodeToJsonElement(
                JsonRpcResponse.serializer(),
                JsonRpcResponse(
                    result = result,
                    id = req.id
                )
            )
        }

        override fun close(cause: Throwable?) {
            emitted = true
        }
    }

    private class NotifyTransformer : JsonRpcTransformer() {
        var receiveCalled = false

        override val isOpen: Boolean
            get() = true

        override suspend fun send(message: JsonElement) {}

        override suspend fun receive(): JsonElement? {
            receiveCalled = true
            error("notify should not wait on receive")
        }

        override fun close(cause: Throwable?) {}
    }
}
