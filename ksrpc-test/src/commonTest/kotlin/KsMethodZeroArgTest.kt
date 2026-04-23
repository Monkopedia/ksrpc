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

import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.internal.HostSerializedChannelImpl
import com.monkopedia.ksrpc.internal.asClient
import com.monkopedia.ksrpc.sockets.asConnection
import io.ktor.utils.io.close
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Integration coverage for issue #40: `@KsMethod` functions with zero value parameters.
 * Exercises a service that mixes 0-arg and 1-arg methods over the in-process SerializedChannel
 * and over the PIPE transport, asserting values round-trip correctly in both directions.
 */
@KsService
interface ZeroArgTestService : RpcService {
    @KsMethod("/ping")
    suspend fun ping(): String

    @KsMethod("/greet")
    suspend fun greet(name: String): String

    @KsMethod("/count")
    suspend fun count(): Int
}

private class ZeroArgTestServiceImpl : ZeroArgTestService {
    override suspend fun ping(): String = "pong"
    override suspend fun greet(name: String): String = "hello $name"
    override suspend fun count(): Int = 42
}

class KsMethodZeroArgTest {

    @Test
    fun inProcessZeroArgRoundTrips() = runBlockingUnit {
        val channel = HostSerializedChannelImpl(ksrpcEnvironment { })
        try {
            val impl: ZeroArgTestService = ZeroArgTestServiceImpl()
            channel.registerDefault(
                impl.serialized(rpcObject<ZeroArgTestService>(), channel.env)
            )
            val stub = rpcObject<ZeroArgTestService>()
                .createStub(channel.asClient.defaultChannel())
            assertEquals("pong", stub.ping())
            assertEquals("hello world", stub.greet("world"))
            assertEquals(42, stub.count())
        } finally {
            channel.close()
        }
    }

    @Test
    fun pipeZeroArgRoundTrips() = runBlockingUnit {
        val env = ksrpcEnvironment { }
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        val serverConnection = (si to output).asConnection(env)
        val clientConnection = (input to so).asConnection(env)
        val serverJob = launch(Dispatchers.Default) {
            val impl: ZeroArgTestService = ZeroArgTestServiceImpl()
            serverConnection.registerDefault(
                impl.serialized(rpcObject<ZeroArgTestService>(), env)
            )
        }
        try {
            val stub = rpcObject<ZeroArgTestService>()
                .createStub(clientConnection.defaultChannel())
            assertEquals("pong", stub.ping())
            assertEquals("hello ksrpc", stub.greet("ksrpc"))
            assertEquals(42, stub.count())
        } finally {
            try {
                clientConnection.close()
            } catch (_: Throwable) {
            }
            try {
                serverConnection.close()
            } catch (_: Throwable) {
            }
            try {
                serverJob.cancel()
            } catch (_: Throwable) {
            }
            try {
                serverJob.join()
            } catch (_: Throwable) {
            }
            try {
                input.cancel(null)
            } catch (_: Throwable) {
            }
            try {
                si.cancel(null)
            } catch (_: Throwable) {
            }
            output.close(null)
            so.close(null)
        }
    }
}
