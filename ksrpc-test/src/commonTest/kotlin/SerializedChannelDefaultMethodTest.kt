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
import com.monkopedia.ksrpc.channels.ChannelClient
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.SerializedService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.builtins.serializer

private class CapturingSerializedService(
    override val env: KsrpcEnvironment<String>
) : SerializedService<String> {
    var lastEndpoint: String? = null
    var lastPayload: String? = null

    override suspend fun call(endpoint: String, input: CallData<String>): CallData<String> {
        lastEndpoint = endpoint
        lastPayload = input.readSerialized()
        return CallData.create("ok")
    }

    override suspend fun close() = Unit

    override suspend fun onClose(onClose: suspend () -> Unit) = Unit
}

private class CapturingChannelClient(
    override val env: KsrpcEnvironment<String>,
    private val service: SerializedService<String>
) : ChannelClient<String> {
    var wrappedId: ChannelId? = null

    override suspend fun wrapChannel(channelId: ChannelId): SerializedService<String> {
        wrappedId = channelId
        return service
    }

    override suspend fun call(
        channelId: ChannelId,
        endpoint: String,
        data: CallData<String>
    ): CallData<String> = error("unused")

    override suspend fun close(id: ChannelId) = Unit

    override suspend fun close() = Unit

    override suspend fun onClose(onClose: suspend () -> Unit) = Unit
}

class SerializedChannelDefaultMethodTest {

    @Test
    fun callWithRpcMethodDelegatesToStringEndpointOverload() = runBlockingUnit {
        val env = ksrpcEnvironment { }
        val service = CapturingSerializedService(env)
        val method =
            RpcMethod<RpcService, String, String>(
                endpoint = "/rpc",
                inputTransform = SerializerTransformer(String.serializer()),
                outputTransform = SerializerTransformer(String.serializer()),
                method = object : ServiceExecutor {
                    override suspend fun invoke(service: RpcService, input: Any?): Any? = "unused"
                }
            )

        val output = service.call(method, CallData.create("input"))

        assertEquals("/rpc", service.lastEndpoint)
        assertEquals("input", service.lastPayload)
        assertEquals("ok", output.readSerialized())
    }

    @Test
    fun defaultChannelWrapsDefaultChannelId() = runBlockingUnit {
        val env = ksrpcEnvironment { }
        val service = CapturingSerializedService(env)
        val client = CapturingChannelClient(env, service)

        client.defaultChannel()

        assertEquals(ChannelClient.DEFAULT, client.wrappedId?.id)
    }
}
