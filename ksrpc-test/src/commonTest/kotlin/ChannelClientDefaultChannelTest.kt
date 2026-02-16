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
import kotlin.test.assertSame

private class ClientDefaultChannelService(
    override val env: KsrpcEnvironment<String>
) : SerializedService<String> {
    override suspend fun call(endpoint: String, input: CallData<String>): CallData<String> = input

    override suspend fun close() = Unit

    override suspend fun onClose(onClose: suspend () -> Unit) = Unit
}

private class CapturingDefaultChannelClient(
    override val env: KsrpcEnvironment<String>,
    private val service: SerializedService<String>
) : ChannelClient<String> {
    val wrappedIds = mutableListOf<ChannelId>()

    override suspend fun wrapChannel(channelId: ChannelId): SerializedService<String> {
        wrappedIds += channelId
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

class ChannelClientDefaultChannelTest {
    @Test
    fun defaultChannelDelegatesToWrapChannelWithDefaultId() = runBlockingUnit {
        val env = ksrpcEnvironment { }
        val service = ClientDefaultChannelService(env)
        val client = CapturingDefaultChannelClient(env, service)

        val default = client.defaultChannel()

        assertSame(service, default)
        assertEquals(1, client.wrappedIds.size)
        assertEquals(ChannelClient.DEFAULT, client.wrappedIds.single().id)
    }
}
