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
@file:OptIn(KsrpcInternal::class)

package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.RpcCallId
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcError
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcRequest
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcResponse
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcServerError
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcServiceWrapper
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcTransformer
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcWriterBase
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Native JSON-RPC error envelope coverage for #78. The transport-native shape
 * `{error: {code, message, data}}` carries [CallData.Error] components verbatim — the
 * error.code is the ksrpc errorCode, error.message is the errorMessage, and error.data is
 * the wire-encoded errorData. Built-in sentinels ([KsrpcException.ENDPOINT_NOT_FOUND_CODE]
 * = `-32601` and [KsrpcException.INTERNAL_ERROR_CODE] = `-32603`) intentionally collide
 * with the JSON-RPC reserved codes so vanilla JSON-RPC consumers see the right semantics
 * without translation.
 */
class JsonRpcTypedErrorRoutingTest {

    @Test
    fun handlerErrorEnvelopeCarriesUserCodeAndPayload() = runBlockingUnit {
        // Server-side: SerializedService returns a CallData.Error with a user code +
        // typed payload. JsonRpcServiceWrapper.execute throws JsonRpcServerError, which
        // launchRequestHandler in JsonRpcWriterBase encodes as {error: {code,message,data}}.
        val request = Json.encodeToJsonElement(
            JsonRpcRequest.serializer(),
            JsonRpcRequest(
                method = "explode",
                params = JsonPrimitive("input"),
                id = JsonPrimitive(7)
            )
        )
        val transformer = QueuedTransformer(request)
        val writer = JsonRpcWriterBase(
            scope = CoroutineScope(coroutineContext + SupervisorJob()),
            context = coroutineContext,
            env = ksrpcEnvironment { },
            comm = transformer
        )

        writer.registerDefault(
            object : SerializedService<String> {
                override val env = ksrpcEnvironment { }
                override val context: CoroutineContext = EmptyCoroutineContext

                override suspend fun call(
                    endpoint: String,
                    input: CallData<String>,
                    callId: RpcCallId?
                ): CallData<String> = CallData.Error(
                    errorCode = 100,
                    errorMessage = "user-coded failure",
                    errorData = "{\"retry\":true}"
                )

                override suspend fun close() {}
                override suspend fun onClose(onClose: suspend () -> Unit) {}
            }
        )

        val sent = withTimeout(2_000) { transformer.firstSent.await() }
        val response = Json.decodeFromJsonElement(JsonRpcResponse.serializer(), sent)
        val error = assertNotNull(response.error)
        assertEquals(100, error.code)
        assertEquals("user-coded failure", error.message)
        // error.data is decoded directly from the wire-encoded errorData, so the JsonObject
        // shape is preserved (no double-encoding).
        val data = error.data
        assertEquals(JsonObject(mapOf("retry" to JsonPrimitive(true))), data)
        assertEquals(JsonPrimitive(7), response.id)
        writer.close()
    }

    @Test
    fun outboundExecuteSurfacesErrorEnvelopeAsJsonRpcServerError() = runBlockingUnit {
        // Client-side: receiving an error envelope surfaces JsonRpcServerError so
        // JsonRpcSerializedChannel can repackage it as CallData.Error for RpcMethod.decodeError.
        val transformer = ErrorEnvelopeReplyTransformer(
            errorCode = 100,
            errorMessage = "user failure",
            errorData = JsonObject(mapOf("retry" to JsonPrimitive(true)))
        )
        val writer = JsonRpcWriterBase(
            scope = CoroutineScope(coroutineContext + SupervisorJob()),
            context = coroutineContext,
            env = ksrpcEnvironment { },
            comm = transformer
        )

        val thrown = assertFailsWith<JsonRpcServerError> {
            writer.execute("ping", JsonPrimitive("x"), isNotify = false, id = null)
        }

        assertEquals(100, thrown.errorCode)
        assertEquals("user failure", thrown.message)
        assertEquals(JsonObject(mapOf("retry" to JsonPrimitive(true))), thrown.data)
        writer.close()
    }

    @Test
    fun jsonRpcServiceWrapperRepackagesErrorAsCallData() = runBlockingUnit {
        // End-to-end: when a backing SerializedService returns CallData.Error, the
        // JsonRpcServiceWrapper.execute surface raises JsonRpcServerError carrying the
        // exact (code, message, data) so the next layer can repackage as needed.
        val backing = object : SerializedService<String> {
            override val env = ksrpcEnvironment { }
            override val context: CoroutineContext = EmptyCoroutineContext

            override suspend fun call(
                endpoint: String,
                input: CallData<String>,
                callId: RpcCallId?
            ): CallData<String> = CallData.Error(
                errorCode = KsrpcException.ENDPOINT_NOT_FOUND_CODE,
                errorMessage = "missing",
                errorData = null
            )

            override suspend fun close() {}
            override suspend fun onClose(onClose: suspend () -> Unit) {}
        }
        val wrapper = JsonRpcServiceWrapper(backing)

        val thrown = assertFailsWith<JsonRpcServerError> {
            wrapper.execute("missing", JsonPrimitive("x"), isNotify = false, id = JsonPrimitive(1))
        }
        assertEquals(KsrpcException.ENDPOINT_NOT_FOUND_CODE, thrown.errorCode)
        assertEquals("missing", thrown.message)
    }

    @Test
    fun sentinelEndpointNotFoundCodeMatchesJsonRpcReservedCode() {
        // `-32601` is intentionally chosen so that JSON-RPC consumers see "method not found"
        // semantics without any translation layer.
        assertEquals(JsonRpcError.METHOD_NOT_FOUND, KsrpcException.ENDPOINT_NOT_FOUND_CODE)
        assertEquals(JsonRpcError.INTERNAL_ERROR, KsrpcException.INTERNAL_ERROR_CODE)
    }

    private class QueuedTransformer(vararg incoming: JsonElement) : JsonRpcTransformer() {
        private val queue = ArrayDeque(incoming.toList())
        val firstSent = CompletableDeferred<JsonElement>()

        override val isOpen: Boolean
            get() = queue.isNotEmpty()

        override suspend fun send(message: JsonElement) {
            if (!firstSent.isCompleted) firstSent.complete(message)
        }

        override suspend fun receive(): JsonElement? = queue.removeFirstOrNull()
        override fun close(cause: Throwable?) {}
    }

    private class ErrorEnvelopeReplyTransformer(
        private val errorCode: Int,
        private val errorMessage: String,
        private val errorData: JsonElement?
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
                    error = JsonRpcError(errorCode, errorMessage, errorData),
                    id = req.id
                )
            )
        }

        override fun close(cause: Throwable?) {
            emitted = true
        }
    }
}
