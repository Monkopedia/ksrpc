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
@file:Suppress("unused", "UNUSED_VARIABLE")

package com.monkopedia.ksrpc.samples

import com.monkopedia.ksrpc.channels.connect
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.websocket.asWebsocketConnection
import com.monkopedia.ksrpc.ktor.websocket.serveWebsocket
import com.monkopedia.ksrpc.toStub
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * Demonstrates setting up a WebSocket server hosting a ksrpc service.
 */
fun websocketServerSetup() {
    val env = ksrpcEnvironment { }
    val service = object : GreetingService {
        override suspend fun greet(name: String): String = "Hello, $name!"
    }

    // Embed ksrpc into a Ktor WebSocket server.
    // The server application must have the WebSockets plugin installed.
    val server = embeddedServer(Netty, port = 8080) {
        install(ServerWebSockets)
        routing {
            serveWebsocket("/ws", service, env)
        }
    }
    // server.start(wait = true)
}

/**
 * Demonstrates connecting to a ksrpc WebSocket server as a client.
 */
suspend fun websocketClientConnect() {
    val env = ksrpcEnvironment { }

    val httpClient = HttpClient {
        install(WebSockets)
    }

    // Create a bidirectional WebSocket connection.
    val connection = httpClient.asWebsocketConnection(
        "ws://localhost:8080/ws",
        env
    )

    // Use connect<Host, Client> to set up both directions at once,
    // or use defaultChannel() for client-only access.
    val stub = connection.defaultChannel().toStub<GreetingService, String>()
    val greeting = stub.greet("world")
}
