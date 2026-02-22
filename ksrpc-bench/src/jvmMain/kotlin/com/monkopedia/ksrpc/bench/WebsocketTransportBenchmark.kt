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
import com.monkopedia.ksrpc.ktor.websocket.asWebsocketConnection
import com.monkopedia.ksrpc.ktor.websocket.serveWebsocket
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

@State(Scope.Benchmark)
open class WebsocketTransportBenchmark {

    @Param("32", "256", "2048")
    var payloadSize: Int = 0

    private val env = ksrpcEnvironment { }
    private lateinit var payload: String
    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var client: HttpClient
    private lateinit var connection: Connection<String>
    private lateinit var clientChannel: SerializedService<String>

    @Setup
    fun setup() = runBlocking {
        payload = "x".repeat(payloadSize)
        val port = reserveFreePort()
        server = embeddedServer(Netty, port) {
            install(ServerWebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
            routing {
                serveWebsocket("/rpc", EchoSerializedService(env), env)
            }
        }.start(wait = false)
        client = HttpClient(OkHttp) {
            install(ClientWebSockets)
        }
        connection = client.asWebsocketConnection("http://127.0.0.1:$port/rpc", env)
        clientChannel = connection.defaultChannel()
    }

    @Benchmark
    fun websocketRoundTrip(): String = runBlocking {
        callEcho(clientChannel, env, payload)
    }

    @TearDown
    fun tearDown() {
        runBlocking {
            runCatching { connection.close() }
            runCatching { client.close() }
            runCatching { server.stop(1_000, 3_000) }
        }
    }
}
