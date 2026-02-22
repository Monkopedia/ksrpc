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
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.sockets.asConnection
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@State(Scope.Benchmark)
open class InputOutputStreamTransportBenchmark {

    @Param("32", "256", "2048")
    var payloadSize: Int = 0

    private val env = ksrpcEnvironment { }
    private lateinit var payload: String
    private lateinit var complexPayload: ComplexEchoPayload
    private lateinit var binaryPayload: ByteArray
    private lateinit var clientInput: PipedInputStream
    private lateinit var clientOutput: PipedOutputStream
    private lateinit var serverInput: PipedInputStream
    private lateinit var serverOutput: PipedOutputStream
    private lateinit var clientConnection: Connection<String>
    private lateinit var serverConnection: Connection<String>
    private lateinit var timedRunner: TimedRunner
    private lateinit var scope: CoroutineScope
    private lateinit var serverJob: Job

    @Setup
    fun setup() {
        payload = "x".repeat(payloadSize)
        complexPayload = createComplexPayload(payloadSize)
        binaryPayload = createBinaryPayload(payloadSize)
        timedRunner = TimedRunner("InputOutputStreamTransportBenchmark")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        timedRunner.run(timeoutMillis = 10_000) {
            val pipeSize = (payloadSize * 16).coerceAtLeast(64 * 1024)
            serverInput = PipedInputStream(pipeSize)
            clientOutput = PipedOutputStream(serverInput)
            clientInput = PipedInputStream(pipeSize)
            serverOutput = PipedOutputStream(clientInput)
            clientConnection = (clientInput to clientOutput).asConnection(env)
            serverConnection = (serverInput to serverOutput).asConnection(env)
            serverJob = scope.launch {
                serverConnection.registerDefault(EchoSerializedService(env))
            }
        }
    }

    @Benchmark
    fun inputOutputRoundTrip(): String = timedRunner.run(timeoutMillis = 5_000) {
        val clientChannel: SerializedService<String> = clientConnection.defaultChannel()
        callEcho(clientChannel, env, payload)
    }

    @Benchmark
    fun inputOutputComplexRoundTrip(): Int = timedRunner.run(timeoutMillis = 5_000) {
        val clientChannel: SerializedService<String> = clientConnection.defaultChannel()
        val response = callComplexEcho(clientChannel, env, complexPayload)
        response.values.size + response.children.size + response.text.length
    }

    @Benchmark
    fun inputOutputBinaryRoundTrip(): Int = timedRunner.run(timeoutMillis = 5_000) {
        val clientChannel: SerializedService<String> = clientConnection.defaultChannel()
        val response = callBinaryEcho(clientChannel, binaryPayload)
        binaryDigest(response)
    }

    @TearDown
    fun tearDown() {
        if (::timedRunner.isInitialized) {
            runCatching {
                timedRunner.run(timeoutMillis = 10_000) {
                    runCatching { clientConnection.close() }
                    runCatching { serverConnection.close() }
                    runCatching { serverJob.cancel() }
                    runCatching { scope.cancel() }
                    runCatching { clientInput.close() }
                    runCatching { clientOutput.close() }
                    runCatching { serverInput.close() }
                    runCatching { serverOutput.close() }
                }
            }
            runCatching { timedRunner.close() }
        }
    }
}
