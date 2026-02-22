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

import com.monkopedia.ksrpc.channels.ChannelClient
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.asConnection
import com.monkopedia.ksrpc.ktor.serve
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown

@State(Scope.Benchmark)
open class HttpTransportBenchmark {

    @Param("32", "256", "2048")
    var payloadSize: Int = 0

    private val env = ksrpcEnvironment { }
    private lateinit var payload: String
    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var client: HttpClient
    private lateinit var connection: ChannelClient<String>
    private lateinit var clientChannel: SerializedService<String>
    private lateinit var timedRunner: TimedRunner

    @Setup
    fun setup() {
        payload = "x".repeat(payloadSize)
        timedRunner = TimedRunner("HttpTransportBenchmark")
        timedRunner.run(timeoutMillis = 15_000) {
            server = embeddedServer(Netty, 0) {
                routing {
                    serve("/rpc", EchoSerializedService(env), env)
                }
            }.start(wait = false)
            val port = server.engine.resolvedConnectors().first().port
            waitForPortOpen("127.0.0.1", port)
            client = HttpClient(OkHttp) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 5_000
                    connectTimeoutMillis = 2_000
                    socketTimeoutMillis = 5_000
                }
            }
            connection = client.asConnection("http://127.0.0.1:$port/rpc", env)
            clientChannel = connection.defaultChannel()
        }
    }

    @Benchmark
    fun httpRoundTrip(): String = timedRunner.run(timeoutMillis = 5_000) {
        callEcho(clientChannel, env, payload)
    }

    @TearDown
    fun tearDown() {
        if (::timedRunner.isInitialized) {
            runCatching {
                timedRunner.run(timeoutMillis = 10_000) {
                    runCatching { connection.close() }
                    runCatching { client.close() }
                    runCatching { server.stop(1_000, 3_000) }
                }
            }
            runCatching { timedRunner.close() }
        }
    }
}
