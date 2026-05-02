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
import com.monkopedia.ksrpc.jsonrpc.asJsonRpcConnection
import com.monkopedia.ksrpc.jsonrpc.stdInJsonRpcConnection
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.serialized
import com.monkopedia.ksrpc.toStub
import io.ktor.utils.io.ByteChannel

/**
 * Demonstrates creating a JSON-RPC 2.0 connection from byte channels.
 */
suspend fun jsonRpcConnection() {
    val env = ksrpcEnvironment { }

    // Any pair of byte read/write channels can be a JSON-RPC connection.
    val readChannel = ByteChannel(autoFlush = true)
    val writeChannel = ByteChannel(autoFlush = true)

    val connection = (readChannel to writeChannel).asJsonRpcConnection(env)

    // Register a service on the connection.
    val service = object : GreetingService {
        override suspend fun greet(name: String): String = "Hello, $name!"
    }
    connection.registerDefault(service.serialized(env))
}

/**
 * Demonstrates using JSON-RPC over stdin/stdout (e.g., for LSP-style protocols).
 */
suspend fun jsonRpcStdIn() {
    val env = ksrpcEnvironment { }

    // stdInJsonRpcConnection creates a SingleChannelConnection over stdin/stdout
    // using JSON-RPC 2.0 framing with Content-Length headers.
    val connection = stdInJsonRpcConnection(env)

    // Use connect to set up both host and client sides.
    connection.connect<GreetingService, GreetingService, String> { remoteService ->
        // remoteService is a stub for the remote side's hosted service.
        // Return the local service to host on this side.
        object : GreetingService {
            override suspend fun greet(name: String): String = "Hello from server, $name!"
        }
    }
}
