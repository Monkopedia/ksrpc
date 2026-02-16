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
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcChannel
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcSerializedChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

class JsonRpcSerializedChannelNotifyTest {

    @Test
    fun testMethodWithoutReturnTypeUsesNotifyAndReturnsUnitPayload() = runBlockingUnit {
        val jsonChannel = CapturingJsonRpcChannel(response = JsonPrimitive("ignored"))
        val serializedChannel =
            JsonRpcSerializedChannel(
                context = coroutineContext,
                channel = jsonChannel,
                env = ksrpcEnvironment { }
            )
        val method =
            RpcMethod<RpcService, String, Unit>(
                endpoint = "notify-endpoint",
                inputTransform = SerializerTransformer(String.serializer()),
                outputTransform = SerializerTransformer(Unit.serializer()),
                method =
                    object : ServiceExecutor {
                        override suspend fun invoke(service: RpcService, input: Any?): Any? = Unit
                    }
            )
        val input = CallData.create(Json.encodeToString(String.serializer(), "payload"))

        val output = serializedChannel.call(method, input)

        assertEquals("notify-endpoint", jsonChannel.method)
        assertEquals(JsonPrimitive("payload"), jsonChannel.message)
        assertTrue(jsonChannel.isNotify)
        assertFalse(output.isBinary)
        Json.decodeFromString(Unit.serializer(), output.readSerialized())
    }

    @Test
    fun testMethodWithReturnTypeUsesRequestResponsePath() = runBlockingUnit {
        val jsonChannel = CapturingJsonRpcChannel(response = JsonPrimitive("pong"))
        val serializedChannel =
            JsonRpcSerializedChannel(
                context = coroutineContext,
                channel = jsonChannel,
                env = ksrpcEnvironment { }
            )
        val method =
            RpcMethod<RpcService, String, String>(
                endpoint = "request-endpoint",
                inputTransform = SerializerTransformer(String.serializer()),
                outputTransform = SerializerTransformer(String.serializer()),
                method =
                    object : ServiceExecutor {
                        override suspend fun invoke(service: RpcService, input: Any?): Any? = "unused"
                    }
            )
        val input = CallData.create(Json.encodeToString(String.serializer(), "payload"))

        val output = serializedChannel.call(method, input)

        assertEquals("request-endpoint", jsonChannel.method)
        assertEquals(JsonPrimitive("payload"), jsonChannel.message)
        assertFalse(jsonChannel.isNotify)
        assertFalse(output.isBinary)
        assertEquals("pong", Json.decodeFromString(String.serializer(), output.readSerialized()))
    }

    @Test
    fun testCloseInvokesRegisteredOnCloseCallbacks() = runBlockingUnit {
        val jsonChannel = CapturingJsonRpcChannel(response = JsonPrimitive("unused"))
        val serializedChannel =
            JsonRpcSerializedChannel(
                context = coroutineContext,
                channel = jsonChannel,
                env = ksrpcEnvironment { }
            )
        val callbacks = mutableSetOf<String>()

        serializedChannel.onClose { callbacks.add("a") }
        serializedChannel.onClose { callbacks.add("b") }
        assertTrue(callbacks.isEmpty())

        serializedChannel.close()

        assertTrue(jsonChannel.closeCalled)
        assertEquals(setOf("a", "b"), callbacks)
    }

    @Test
    fun testStringEndpointCallUsesRequestResponsePath() = runBlockingUnit {
        val jsonChannel = CapturingJsonRpcChannel(response = JsonPrimitive("pong"))
        val serializedChannel =
            JsonRpcSerializedChannel(
                context = coroutineContext,
                channel = jsonChannel,
                env = ksrpcEnvironment { }
            )
        val input = CallData.create(Json.encodeToString(String.serializer(), "ping"))

        val output = serializedChannel.call("echo", input)

        assertEquals("echo", jsonChannel.method)
        assertEquals(JsonPrimitive("ping"), jsonChannel.message)
        assertFalse(jsonChannel.isNotify)
        assertFalse(output.isBinary)
        assertEquals("pong", Json.decodeFromString(String.serializer(), output.readSerialized()))
    }

    @Test
    fun testStringEndpointCallSerializesNullResponse() = runBlockingUnit {
        val jsonChannel = CapturingJsonRpcChannel(response = null)
        val serializedChannel =
            JsonRpcSerializedChannel(
                context = coroutineContext,
                channel = jsonChannel,
                env = ksrpcEnvironment { }
            )
        val input = CallData.create(Json.encodeToString(String.serializer(), "ping"))

        val output = serializedChannel.call("nullable", input)

        assertEquals("nullable", jsonChannel.method)
        assertEquals(JsonPrimitive("ping"), jsonChannel.message)
        assertFalse(jsonChannel.isNotify)
        assertFalse(output.isBinary)
        assertEquals("null", output.readSerialized())
    }

    @Test
    fun testStringEndpointCallRejectsMalformedJsonPayload() = runBlockingUnit {
        val jsonChannel = CapturingJsonRpcChannel(response = JsonPrimitive("unused"))
        val serializedChannel =
            JsonRpcSerializedChannel(
                context = coroutineContext,
                channel = jsonChannel,
                env = ksrpcEnvironment { }
            )

        assertFailsWith<Throwable> {
            serializedChannel.call("bad-json", CallData.create("{"))
        }
        assertEquals(null, jsonChannel.method)
    }

    private class CapturingJsonRpcChannel(
        private val response: JsonElement?
    ) : JsonRpcChannel {
        override val env = ksrpcEnvironment { }
        var method: String? = null
        var message: JsonElement? = null
        var isNotify: Boolean = false
        var closeCalled: Boolean = false

        override suspend fun execute(
            method: String,
            message: JsonElement?,
            isNotify: Boolean
        ): JsonElement? {
            this.method = method
            this.message = message
            this.isNotify = isNotify
            return response
        }

        override suspend fun close() {
            closeCalled = true
        }
    }
}
