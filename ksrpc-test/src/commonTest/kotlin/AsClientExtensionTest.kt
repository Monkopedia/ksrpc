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
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.SerializedChannel
import com.monkopedia.ksrpc.internal.asClient
import kotlin.test.Test
import kotlin.test.assertEquals

private class CapturingSerializedChannel(
    override val env: KsrpcEnvironment<String>
) : SerializedChannel<String> {
    var lastCallChannelId: ChannelId? = null
    var lastEndpoint: String? = null
    var closedChannelId: ChannelId? = null

    override suspend fun call(
        channelId: ChannelId,
        endpoint: String,
        data: CallData<String>
    ): CallData<String> {
        lastCallChannelId = channelId
        lastEndpoint = endpoint
        return data
    }

    override suspend fun close(id: ChannelId) {
        closedChannelId = id
    }

    override suspend fun close() = Unit

    override suspend fun onClose(onClose: suspend () -> Unit) = Unit
}

class AsClientExtensionTest {
    @Test
    fun asClientWrapChannelRoutesCallAndCloseThroughChannelId() = runBlockingUnit {
        val env = ksrpcEnvironment { }
        val base = CapturingSerializedChannel(env)
        val serviceId = ChannelId("service-123")
        val client = base.asClient
        val wrapped = client.wrapChannel(serviceId)

        val output = wrapped.call("echo", CallData.create("payload"))
        wrapped.close()

        assertEquals("service-123", base.lastCallChannelId?.id)
        assertEquals("echo", base.lastEndpoint)
        assertEquals("payload", output.readSerialized())
        assertEquals("service-123", base.closedChannelId?.id)
    }
}
