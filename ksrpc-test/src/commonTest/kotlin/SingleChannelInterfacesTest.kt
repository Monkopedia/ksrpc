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
import com.monkopedia.ksrpc.channels.SingleChannelClient
import com.monkopedia.ksrpc.channels.SingleChannelHost
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

private class SingleChannelTestService(override val env: KsrpcEnvironment<String>) :
    SerializedService<String> {
    override suspend fun call(endpoint: String, input: CallData<String>): CallData<String> = input

    override suspend fun close() = Unit

    override suspend fun onClose(onClose: suspend () -> Unit) = Unit
}

private class CapturingSingleHost(override val env: KsrpcEnvironment<String>) :
    SingleChannelHost<String> {
    var service: SerializedService<String>? = null

    override suspend fun registerDefault(service: SerializedService<String>) {
        this.service = service
    }
}

private class CapturingSingleClient(private val service: SerializedService<String>) :
    SingleChannelClient<String> {
    override suspend fun defaultChannel(): SerializedService<String> = service
}

class SingleChannelInterfacesTest {
    @Test
    fun singleChannelHostRegistersDefaultService() = runBlockingUnit {
        val env = ksrpcEnvironment { }
        val host = CapturingSingleHost(env)
        val service = SingleChannelTestService(env)

        host.registerDefault(service)

        assertNotNull(host.service)
        assertSame(service, host.service)
    }

    @Test
    fun singleChannelClientReturnsDefaultService() = runBlockingUnit {
        val env = ksrpcEnvironment { }
        val service = SingleChannelTestService(env)
        val client = CapturingSingleClient(service)

        val output = client.defaultChannel()

        assertSame(service, output)
    }
}
