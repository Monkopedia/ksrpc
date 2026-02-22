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
package com.monkopedia.ksrpc.bench

import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.sockets.asConnection
import io.ktor.utils.io.ByteChannel
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.coroutines.runBlocking

@State(Scope.Benchmark)
open class SocketTransportBenchmark {

    @Param("32", "256", "2048")
    var payloadSize: Int = 0

    private val env = ksrpcEnvironment { }
    private lateinit var payload: String
    private lateinit var clientConnection: Connection<String>
    private lateinit var serverConnection: Connection<String>
    private lateinit var clientChannel: SerializedService<String>

    @Setup
    fun setup() = runBlocking {
        payload = "x".repeat(payloadSize)
        val clientToServer = ByteChannel(autoFlush = true)
        val serverToClient = ByteChannel(autoFlush = true)
        clientConnection = (serverToClient to clientToServer).asConnection(env)
        serverConnection = (clientToServer to serverToClient).asConnection(env)
        serverConnection.registerDefault(EchoSerializedService(env))
        clientChannel = clientConnection.defaultChannel()
    }

    @Benchmark
    fun socketRoundTrip(): String = runBlocking {
        callEcho(clientChannel, env, payload)
    }

    @TearDown
    fun tearDown() {
        runBlocking {
            runCatching { clientConnection.close() }
            runCatching { serverConnection.close() }
        }
    }
}
