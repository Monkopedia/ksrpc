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

import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.channels.connect
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.serialized
import com.monkopedia.ksrpc.sockets.asConnection
import com.monkopedia.ksrpc.toStub
import io.ktor.utils.io.ByteChannel

// ---- Bidirectional service pair ----

@KsService
interface ServerApi : RpcService {
    @KsMethod("/fetch_data")
    suspend fun fetchData(key: String): String
}

@KsService
interface ClientApi : RpcService {
    @KsMethod("/on_update")
    suspend fun onUpdate(data: String)
}

// ---- Sample functions ----

/**
 * Demonstrates setting up a bidirectional connection with registerDefault and defaultChannel.
 */
suspend fun bidirectionalSetup() {
    val env = ksrpcEnvironment { }

    val readChannel = ByteChannel(autoFlush = true)
    val writeChannel = ByteChannel(autoFlush = true)
    val connection = (readChannel to writeChannel).asConnection(env)

    // Host side: register the server's service.
    val serverService = object : ServerApi {
        override suspend fun fetchData(key: String): String = "value-for-$key"
    }
    connection.registerDefault(serverService.serialized(env))

    // Client side: get the remote service via defaultChannel.
    val remoteStub = connection.defaultChannel().toStub<ServerApi, String>()
    val data = remoteStub.fetchData("my-key")
}

/**
 * Demonstrates the connect helper for bidirectional service setup.
 */
suspend fun connectHelper() {
    val env = ksrpcEnvironment { }

    val readChannel = ByteChannel(autoFlush = true)
    val writeChannel = ByteChannel(autoFlush = true)
    val connection = (readChannel to writeChannel).asConnection(env)

    // connect<Host, Client> wires both directions in one call:
    // - It gets the remote's default channel as a Client stub
    // - The lambda returns the Host implementation to register locally
    connection.connect<ServerApi, ClientApi, String> { clientStub ->
        // clientStub is a typed proxy for the remote's ClientApi.
        // Return the local ServerApi implementation to host.
        object : ServerApi {
            override suspend fun fetchData(key: String): String {
                // Can call back to the client within the handler.
                clientStub.onUpdate("fetching $key")
                return "value-for-$key"
            }
        }
    }
}
