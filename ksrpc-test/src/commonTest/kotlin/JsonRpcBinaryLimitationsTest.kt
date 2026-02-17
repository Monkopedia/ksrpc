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
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcChannel
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcSerializedChannel
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcServiceWrapper
import io.ktor.utils.io.ByteReadChannel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

class JsonRpcBinaryLimitationsTest {

    @Test
    fun testJsonRpcSerializedChannelRejectsBinaryInput() = runBlockingUnit {
        var executed = false
        val jsonRpcChannel =
            object : JsonRpcChannel {
                override val env = ksrpcEnvironment { }

                override suspend fun execute(
                    method: String,
                    message: JsonElement?,
                    isNotify: Boolean
                ): JsonElement? {
                    executed = true
                    return JsonPrimitive("unused")
                }

                override suspend fun close() {}
            }

        val serializedChannel =
            JsonRpcSerializedChannel(
                context = EmptyCoroutineContext,
                channel = jsonRpcChannel,
                env = ksrpcEnvironment { }
            )

        val exception =
            assertFailsWith<IllegalArgumentException> {
                serializedChannel.call(
                    endpoint = "binaryEndpoint",
                    input = CallData.createBinary(ByteReadChannel(byteArrayOf(1, 2, 3)))
                )
            }

        assertTrue((exception.message ?: "").contains("JsonRpc does not support binary data"))
        assertFalse(executed)
    }

    @Test
    fun testJsonRpcServiceWrapperRejectsBinaryResponse() = runBlockingUnit {
        val service =
            object : SerializedService<String> {
                override val env = ksrpcEnvironment { }
                override val context: CoroutineContext = EmptyCoroutineContext

                override suspend fun call(
                    endpoint: String,
                    input: CallData<String>
                ): CallData<String> = CallData.createBinary(ByteReadChannel(byteArrayOf(9, 9, 9)))

                override suspend fun close() {}

                override suspend fun onClose(onClose: suspend () -> Unit) {}
            }

        val wrapper = JsonRpcServiceWrapper(service)

        val exception =
            assertFailsWith<IllegalArgumentException> {
                wrapper.execute(
                    method = "binaryEndpoint",
                    message = JsonPrimitive("message"),
                    isNotify = false
                )
            }

        assertTrue((exception.message ?: "").contains("JsonRpc does not support binary data"))
    }

    @Test
    fun testJsonRpcServiceWrapperRejectsBinaryResponseForNotify() = runBlockingUnit {
        val service =
            object : SerializedService<String> {
                override val env = ksrpcEnvironment { }
                override val context: CoroutineContext = EmptyCoroutineContext

                override suspend fun call(
                    endpoint: String,
                    input: CallData<String>
                ): CallData<String> = CallData.createBinary(ByteReadChannel(byteArrayOf(7, 7, 7)))

                override suspend fun close() {}

                override suspend fun onClose(onClose: suspend () -> Unit) {}
            }

        val wrapper = JsonRpcServiceWrapper(service)

        val exception =
            assertFailsWith<IllegalArgumentException> {
                wrapper.execute(
                    method = "binaryEndpoint",
                    message = JsonPrimitive("message"),
                    isNotify = true
                )
            }

        assertTrue((exception.message ?: "").contains("JsonRpc does not support binary data"))
    }

    @Test
    fun testJsonRpcServiceWrapperForwardsEndpointAndNullPayload() = runBlockingUnit {
        var calledEndpoint: String? = null
        var calledPayload: String? = null
        val service =
            object : SerializedService<String> {
                override val env = ksrpcEnvironment { }
                override val context: CoroutineContext = EmptyCoroutineContext

                override suspend fun call(
                    endpoint: String,
                    input: CallData<String>
                ): CallData<String> {
                    calledEndpoint = endpoint
                    calledPayload = input.readSerialized()
                    return CallData.create("\"ok\"")
                }

                override suspend fun close() {}

                override suspend fun onClose(onClose: suspend () -> Unit) {}
            }

        val wrapper = JsonRpcServiceWrapper(service)
        val response = wrapper.execute(method = "ping", message = null, isNotify = true)

        assertEquals("ping", calledEndpoint)
        assertEquals("null", calledPayload)
        assertEquals(JsonPrimitive("ok"), response)
    }

    @Test
    fun testJsonRpcServiceWrapperForwardsEndpointAndNonNullPayload() = runBlockingUnit {
        var calledEndpoint: String? = null
        var calledPayload: String? = null
        val service =
            object : SerializedService<String> {
                override val env = ksrpcEnvironment { }
                override val context: CoroutineContext = EmptyCoroutineContext

                override suspend fun call(
                    endpoint: String,
                    input: CallData<String>
                ): CallData<String> {
                    calledEndpoint = endpoint
                    calledPayload = input.readSerialized()
                    return CallData.create("\"ok\"")
                }

                override suspend fun close() {}

                override suspend fun onClose(onClose: suspend () -> Unit) {}
            }

        val wrapper = JsonRpcServiceWrapper(service)
        val response = wrapper.execute(
            method = "ping",
            message = JsonPrimitive("hello"),
            isNotify = false
        )

        assertEquals("ping", calledEndpoint)
        assertEquals("\"hello\"", calledPayload)
        assertEquals(JsonPrimitive("ok"), response)
    }

    @Test
    fun testJsonRpcServiceWrapperForwardsNotifyPayload() = runBlockingUnit {
        var calledEndpoint: String? = null
        var calledPayload: String? = null
        val service =
            object : SerializedService<String> {
                override val env = ksrpcEnvironment { }
                override val context: CoroutineContext = EmptyCoroutineContext

                override suspend fun call(
                    endpoint: String,
                    input: CallData<String>
                ): CallData<String> {
                    calledEndpoint = endpoint
                    calledPayload = input.readSerialized()
                    return CallData.create("\"ok\"")
                }

                override suspend fun close() {}

                override suspend fun onClose(onClose: suspend () -> Unit) {}
            }

        val wrapper = JsonRpcServiceWrapper(service)
        val response = wrapper.execute(
            method = "notifyPing",
            message = JsonPrimitive("hello"),
            isNotify = true
        )

        assertEquals("notifyPing", calledEndpoint)
        assertEquals("\"hello\"", calledPayload)
        assertEquals(JsonPrimitive("ok"), response)
    }

    @Test
    fun testJsonRpcServiceWrapperCloseDelegatesToService() = runBlockingUnit {
        var closeCalled = false
        val service =
            object : SerializedService<String> {
                override val env = ksrpcEnvironment { }
                override val context: CoroutineContext = EmptyCoroutineContext

                override suspend fun call(
                    endpoint: String,
                    input: CallData<String>
                ): CallData<String> = error("unused")

                override suspend fun close() {
                    closeCalled = true
                }

                override suspend fun onClose(onClose: suspend () -> Unit) {}
            }

        val wrapper = JsonRpcServiceWrapper(service)
        wrapper.close()

        assertTrue(closeCalled)
    }

    @Test
    fun testJsonRpcServiceWrapperThrowsForMalformedSerializedResponse() = runBlockingUnit {
        val service =
            object : SerializedService<String> {
                override val env = ksrpcEnvironment { }
                override val context: CoroutineContext = EmptyCoroutineContext

                override suspend fun call(
                    endpoint: String,
                    input: CallData<String>
                ): CallData<String> = CallData.create("{")

                override suspend fun close() {}

                override suspend fun onClose(onClose: suspend () -> Unit) {}
            }

        val wrapper = JsonRpcServiceWrapper(service)

        assertFailsWith<Throwable> {
            wrapper.execute(method = "ping", message = JsonPrimitive("input"), isNotify = false)
        }
    }
}
