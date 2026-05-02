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
import com.monkopedia.ksrpc.serialized
import com.monkopedia.ksrpc.sockets.asConnection
import com.monkopedia.ksrpc.sockets.withStdInOut
import com.monkopedia.ksrpc.toStub
import io.ktor.utils.io.ByteChannel

/**
 * Demonstrates hosting a ksrpc service over stdin/stdout.
 */
suspend fun stdInOutHosting() {
    val env = ksrpcEnvironment { }
    val service = object : GreetingService {
        override suspend fun greet(name: String): String = "Hello, $name!"
    }

    // withStdInOut creates a bidirectional Connection over stdin/stdout.
    // This is useful for CLI tools that communicate via pipes.
    withStdInOut(env) { connection ->
        connection.registerDefault(service.serialized(env))
        // Connection stays open until the process ends or the connection closes.
    }
}

/**
 * Demonstrates creating a connection from a pair of byte channels.
 */
suspend fun socketServerSetup() {
    val env = ksrpcEnvironment { }

    // Create a pair of byte channels (simulating a socket pair).
    val readChannel = ByteChannel(autoFlush = true)
    val writeChannel = ByteChannel(autoFlush = true)

    // Any Pair<ByteReadChannel, ByteWriteChannel> can become a Connection.
    val connection = (readChannel to writeChannel).asConnection(env)

    // Register a service on the connection.
    val service = object : GreetingService {
        override suspend fun greet(name: String): String = "Hello, $name!"
    }
    connection.registerDefault(service.serialized(env))
}
