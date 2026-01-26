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
package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.toStub
import com.monkopedia.ksrpc.webworker.createServiceWorkerWithConnection
import com.monkopedia.ksrpc.webworker.test.WebWorkerTestService
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceWorkerTest {
    @Test
    fun wasmServiceWorkerConnection() = runBlockingUnit {
        if (!hasWindow()) {
            println("No window found")
            return@runBlockingUnit
        }
        val env = ksrpcEnvironment { }
        val connection = createServiceWorkerWithConnection(wasmWorkerUrl(), env)
        val service = connection.defaultChannel().toStub<WebWorkerTestService, String>()

        assertEquals("pong:ping:js", service.ping("ping"))
        assertEquals("hello world", service.rpc("hello" to "world"))

        val sub = service.subservice("sub")
        assertEquals("sub a b", sub.rpc("a" to "b"))

        connection.close()
        null
    }
}

private fun wasmWorkerUrl(): String = "base/kotlin/ksrpc-web-worker-test.js"

private val hasWindow: () -> Boolean =
    js("() => typeof window !== 'undefined'")
