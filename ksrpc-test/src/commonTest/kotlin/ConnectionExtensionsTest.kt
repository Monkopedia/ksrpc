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
import com.monkopedia.ksrpc.channels.SingleChannelConnection
import com.monkopedia.ksrpc.channels.connect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

private class TestSerializedService(override val env: KsrpcEnvironment<String>) :
    SerializedService<String> {
    override suspend fun call(endpoint: String, input: CallData<String>): CallData<String> = input

    override suspend fun close() = Unit

    override suspend fun onClose(onClose: suspend () -> Unit) = Unit
}

private class CapturingSingleChannelConnection(
    override val env: KsrpcEnvironment<String>,
    private val channel: SerializedService<String>
) : SingleChannelConnection<String> {
    var defaultChannelCalls = 0
    var registered: SerializedService<String>? = null

    override suspend fun defaultChannel(): SerializedService<String> {
        defaultChannelCalls += 1
        return channel
    }

    override suspend fun registerDefault(service: SerializedService<String>) {
        registered = service
    }
}

class ConnectionExtensionsTest {
    @Test
    fun connectSerializedUsesDefaultChannelAndRegistersReturnedService() = runBlockingUnit {
        val env = ksrpcEnvironment { }
        val incoming = TestSerializedService(env)
        val hosted = TestSerializedService(env)
        val connection = CapturingSingleChannelConnection(env, incoming)
        var received: SerializedService<String>? = null

        connection.connect<String> { service: SerializedService<String> ->
            received = service
            hosted
        }

        assertEquals(1, connection.defaultChannelCalls)
        assertSame(incoming, received)
        assertSame(hosted, connection.registered)
    }
}
