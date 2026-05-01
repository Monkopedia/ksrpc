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

package com.monkopedia.ksrpc.webworker.test

import com.monkopedia.ksrpc.annotation.ExperimentalKsrpc
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.webworker.onServiceWorkerConnection

/**
 * Pre-baked service catalog. Each entry maps a service name to a function that
 * registers the corresponding service on a [Connection]. New test fixtures can
 * be added here without touching the entry-point wiring.
 */
private val serviceCatalog: Map<String, suspend (Connection<String>) -> Unit> = mapOf(
    "WebWorkerTestService" to { connection ->
        connection.registerDefault(WebWorkerTestServiceImpl("js"))
    }
)

fun main() {
    val env = ksrpcEnvironment { }
    onServiceWorkerConnection(env) { connection, serviceName ->
        val registrar = if (serviceName != null) {
            serviceCatalog[serviceName]
                ?: error("Unknown service in catalog: $serviceName")
        } else {
            // Default: register WebWorkerTestService for backward compatibility.
            serviceCatalog.getValue("WebWorkerTestService")
        }
        registrar(connection)
    }
}
