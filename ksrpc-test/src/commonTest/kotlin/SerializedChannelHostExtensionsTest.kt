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

import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.ChannelHost
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.channels.registerHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.serialization.builtins.serializer

@KsService
public interface HostExtensionTestService : RpcService {
    @KsMethod("/echo")
    suspend fun echo(input: String): String
}

private class CapturingHost(override val env: KsrpcEnvironment<String>) : ChannelHost<String> {
    var hostedService: SerializedService<String>? = null
    var defaultService: SerializedService<String>? = null

    override suspend fun registerHost(service: SerializedService<String>): ChannelId {
        hostedService = service
        return ChannelId("captured-host-id")
    }

    override suspend fun registerDefault(service: SerializedService<String>) {
        defaultService = service
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

class SerializedChannelHostExtensionsTest {

    @Test
    fun registerHostReifiedExtensionBuildsHostSerializedService() = runBlockingUnit {
        val env = ksrpcEnvironment { }
        val host = CapturingHost(env)
        val service = object : HostExtensionTestService {
            override suspend fun echo(input: String): String = "$input!"
        }

        val channelId = host.registerHost(service)
        val serialized = assertNotNull(host.hostedService)
        val input = env.serialization.createCallData(String.serializer(), "ping")
        val output = serialized.call("echo", input)
        val decoded = env.serialization.decodeCallData(String.serializer(), output)

        assertEquals("captured-host-id", channelId.id)
        assertEquals("ping!", decoded)
    }

    @Test
    fun registerDefaultReifiedExtensionBuildsHostSerializedService() = runBlockingUnit {
        val env = ksrpcEnvironment { }
        val host = CapturingHost(env)
        val service = object : HostExtensionTestService {
            override suspend fun echo(input: String): String = "$input?"
        }

        host.registerDefault(service)
        val serialized = assertNotNull(host.defaultService)
        val input = env.serialization.createCallData(String.serializer(), "pong")
        val output = serialized.call("echo", input)
        val decoded = env.serialization.decodeCallData(String.serializer(), output)

        assertEquals("pong?", decoded)
    }

    @Test
    fun registerHostExplicitObjectExtensionBuildsHostSerializedService() = runBlockingUnit {
        val env = ksrpcEnvironment { }
        val host = CapturingHost(env)
        val service = object : HostExtensionTestService {
            override suspend fun echo(input: String): String = "$input#"
        }

        val channelId = host.registerHost(service, rpcObject())
        val serialized = assertNotNull(host.hostedService)
        val input = env.serialization.createCallData(String.serializer(), "zip")
        val output = serialized.call("echo", input)
        val decoded = env.serialization.decodeCallData(String.serializer(), output)

        assertEquals("captured-host-id", channelId.id)
        assertEquals("zip#", decoded)
    }

    @Test
    fun registerDefaultExplicitObjectExtensionBuildsHostSerializedService() = runBlockingUnit {
        val env = ksrpcEnvironment { }
        val host = CapturingHost(env)
        val service = object : HostExtensionTestService {
            override suspend fun echo(input: String): String = "$input*"
        }

        host.registerDefault(service, rpcObject())
        val serialized = assertNotNull(host.defaultService)
        val input = env.serialization.createCallData(String.serializer(), "zap")
        val output = serialized.call("echo", input)
        val decoded = env.serialization.decodeCallData(String.serializer(), output)

        assertEquals("zap*", decoded)
    }
}
