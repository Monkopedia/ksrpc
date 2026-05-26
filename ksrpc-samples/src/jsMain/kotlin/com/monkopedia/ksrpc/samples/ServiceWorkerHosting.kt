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
@file:OptIn(ExperimentalKsrpc::class)
@file:Suppress("unused", "UNUSED_VARIABLE")

package com.monkopedia.ksrpc.samples

import com.monkopedia.ksrpc.annotation.ExperimentalKsrpc
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.serialized
import com.monkopedia.ksrpc.toStub
import com.monkopedia.ksrpc.webworker.createServiceWorkerWithConnection
import com.monkopedia.ksrpc.webworker.onServiceWorkerConnection

/**
 * Demonstrates hosting a ksrpc service inside a browser service worker.
 *
 * This runs in the worker script. `onServiceWorkerConnection` listens for
 * incoming `"ksrpc-connect"` messages and establishes a `Connection` for each
 * client, on which the service is registered.
 */
fun serviceWorkerHosting() {
    val env = ksrpcEnvironment { }
    val service = object : GreetingService {
        override suspend fun greet(name: String): String = "Hello, $name!"
    }
    onServiceWorkerConnection(env) { connection ->
        connection.registerDefault(service.serialized(env))
    }
}

/**
 * Demonstrates connecting to a service worker from the main thread.
 *
 * `createServiceWorkerWithConnection` registers the worker script and returns a
 * `Connection` whose default channel can be turned into a stub.
 */
suspend fun serviceWorkerClient() {
    val env = ksrpcEnvironment { }
    val connection = createServiceWorkerWithConnection("/worker.js", env)
    val service = connection.defaultChannel().toStub<GreetingService, String>()
}
