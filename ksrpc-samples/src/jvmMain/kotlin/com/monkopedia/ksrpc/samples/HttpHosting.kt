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

import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.asHttpChannelClient
import com.monkopedia.ksrpc.ktor.serveHttp
import com.monkopedia.ksrpc.toStub
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing

/**
 * Demonstrates setting up an HTTP server hosting a ksrpc service.
 */
fun httpServerSetup() {
    val env = ksrpcEnvironment { }
    val service = object : GreetingService {
        override suspend fun greet(name: String): String = "Hello, $name!"
    }

    // Embed ksrpc into a Ktor HTTP server.
    val server = embeddedServer(Netty, port = 8080) {
        routing {
            serveHttp("/api", service, env)
        }
    }
    // server.start(wait = true)
}

/**
 * Demonstrates connecting to a ksrpc HTTP server as a client.
 */
suspend fun httpClientConnect() {
    val env = ksrpcEnvironment { }

    val httpClient = HttpClient(OkHttp)

    // Turn the HttpClient into a ksrpc ChannelClient for the given URL.
    val channelClient = httpClient.asHttpChannelClient(
        "http://localhost:8080/api",
        env
    )

    // Get the default channel and create a typed stub.
    val stub = channelClient.defaultChannel().toStub<GreetingService, String>()
    val greeting = stub.greet("world")
}
