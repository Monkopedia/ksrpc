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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class JsonRpcKsrpcExceptionTest {

    @Test
    fun errorResponseThrowsKsrpcExceptionWithCodeAndMessage() = runBlockingUnit {
        val transformer = ErrorTransformer(
            errorCode = JsonRpcError.INTERNAL_ERROR,
            errorMessage = "internal failure"
        )
        val writer = JsonRpcWriterBase(
            scope = CoroutineScope(coroutineContext + SupervisorJob()),
            context = coroutineContext,
            env = ksrpcEnvironment { },
            comm = transformer
        )

        val thrown = assertFailsWith<KsrpcException> {
            writer.execute("test", JsonPrimitive("input"), isNotify = false, id = null)
        }

        assertEquals(JsonRpcError.INTERNAL_ERROR, thrown.code)
        assertEquals("internal failure", thrown.message)
        assertNull(thrown.data)
        writer.close()
    }

    @Test
    fun errorResponseWithDataCarriesSerializedData() = runBlockingUnit {
        val errorData = buildJsonObject {
            put("errorCode", "E001")
            put("description", "validation failed")
        }
        val transformer = ErrorTransformer(
            errorCode = -32000,
            errorMessage = "validation error",
            errorData = errorData
        )
        val writer = JsonRpcWriterBase(
            scope = CoroutineScope(coroutineContext + SupervisorJob()),
            context = coroutineContext,
            env = ksrpcEnvironment { },
            comm = transformer
        )

        val thrown = assertFailsWith<KsrpcException> {
            writer.execute("test", JsonPrimitive("input"), isNotify = false, id = null)
        }

        assertEquals(-32000, thrown.code)
        assertEquals("validation error", thrown.message)
        val data = assertNotNull(thrown.data)
        // The data should be the JSON serialization of the errorData object
        assertTrue(data.contains("E001"))
        assertTrue(data.contains("validation failed"))
        writer.close()
    }

    @Test
    fun errorResponseWithNullDataHasNullDataField() = runBlockingUnit {
        val transformer = ErrorTransformer(
            errorCode = -32603,
            errorMessage = "no data",
            errorData = null
        )
        val writer = JsonRpcWriterBase(
            scope = CoroutineScope(coroutineContext + SupervisorJob()),
            context = coroutineContext,
            env = ksrpcEnvironment { },
            comm = transformer
        )

        val thrown = assertFailsWith<KsrpcException> {
            writer.execute("test", JsonPrimitive("input"), isNotify = false, id = null)
        }

        assertEquals(-32603, thrown.code)
        assertNull(thrown.data)
        writer.close()
    }

    @Test
    fun methodWithoutKsErrorDataStillThrowsKsrpcException() = runBlockingUnit {
        val transformer = ErrorTransformer(
            errorCode = -32603,
            errorMessage = "plain error"
        )
        val writer = JsonRpcWriterBase(
            scope = CoroutineScope(coroutineContext + SupervisorJob()),
            context = coroutineContext,
            env = ksrpcEnvironment { },
            comm = transformer
        )

        val thrown = assertFailsWith<KsrpcException> {
            writer.execute("test", JsonPrimitive("input"), isNotify = false, id = null)
        }

        assertEquals(-32603, thrown.code)
        assertEquals("plain error", thrown.message)
        writer.close()
    }

    private class ErrorTransformer(
        private val errorCode: Int = JsonRpcError.INTERNAL_ERROR,
        private val errorMessage: String = "error",
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
