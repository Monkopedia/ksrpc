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
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.sockets.asConnection
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer

class SocketInputOutputStreamsJvmTest {
    @Test
    fun inputOutputPairAsConnectionSupportsShortLivedSetupContext() {
        val env = ksrpcEnvironment { }
        val setupExecutor = Executors.newSingleThreadExecutor()
        var clientConnection: Connection<String>? = null
        var serverConnection: Connection<String>? = null
        try {
            val setupFuture = setupExecutor.submit<Pair<Connection<String>, Connection<String>>> {
                runBlocking {
                    val pipeSize = 64 * 1024
                    val serverInput = PipedInputStream(pipeSize)
                    val clientOutput = PipedOutputStream(serverInput)
                    val clientInput = PipedInputStream(pipeSize)
                    val serverOutput = PipedOutputStream(clientInput)
                    (clientInput to clientOutput).asConnection(env) to
                        (serverInput to serverOutput).asConnection(env)
                }
            }
            val (client, server) = setupFuture.get(3, TimeUnit.SECONDS)
            clientConnection = client
            serverConnection = server

            runBlocking {
                val localServerConnection = checkNotNull(serverConnection)
                val localClientConnection = checkNotNull(clientConnection)
                localServerConnection.registerDefault(EchoSerializedService(env))
                val request = env.serialization.createCallData(String.serializer(), "hello")
                val response = localClientConnection.defaultChannel().call("echo", request)
                val decoded = env.serialization.decodeCallData(String.serializer(), response)
                assertEquals("hello", decoded)
            }
        } finally {
            runBlocking {
                runCatching { clientConnection?.close() }
                runCatching { serverConnection?.close() }
            }
            setupExecutor.shutdownNow()
        }
    }

    private class EchoSerializedService(override val env: KsrpcEnvironment<String>) :
        SerializedService<String> {
        override suspend fun call(endpoint: String, input: CallData<String>): CallData<String> =
            input

        override suspend fun close() = Unit

        override suspend fun onClose(onClose: suspend () -> Unit) = Unit
    }
}
