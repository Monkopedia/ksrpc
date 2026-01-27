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

import com.monkopedia.ksrpc.webworker.createServiceWorkerWithConnection
import com.monkopedia.ksrpc.webworker.test.WebWorkerTestService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceWorkerTest {
    @Test
    fun wasmServiceWorkerConnection_methods() = runBlockingUnit {
        if (!hasWindow()) return@runBlockingUnit

        useWebWorkerService { service ->
            assertEquals("pong:ping:js", service.ping("ping"))
            assertEquals("hello world", service.rpc("hello" to "world"))
        }

        null
    }

    @Test
    fun wasmServiceWorkerConnection_subservice() = runBlockingUnit {
        if (!hasWindow()) return@runBlockingUnit
        useWebWorkerService { service ->
            service.subservice("sub").use { sub ->
                assertEquals("sub a b", sub.rpc("a" to "b"))
            }
        }
        null
    }

    @Test
    fun wasmServiceWorkerConnection_introspection() = runBlockingUnit {
        if (!hasWindow()) return@runBlockingUnit
        useWebWorkerService { service ->
            service.getIntrospection().use { introspection ->
                val serviceName = introspection.getServiceName()
                assertEquals(
                    "com.monkopedia.ksrpc.webworker.test.WebWorkerTestService",
                    serviceName
                )

                val endpoints = introspection.getEndpoints()
                assertTrue("ping" in endpoints)
                assertTrue("rpc" in endpoints)
                assertTrue("service" in endpoints)
            }
        }

        null
    }

    @Test
    fun wasmServiceWorkerConnection_introspection_introspection() = runBlockingUnit {
        if (!hasWindow()) {
            println("No window found")
            return@runBlockingUnit
        }
        useWebWorkerService { service ->
            service.getIntrospection().getIntrospection().use { introspection ->
                val serviceName = introspection.getServiceName()
                assertEquals("com.monkopedia.ksrpc.IntrospectionService", serviceName)

                val endpoints = introspection.getEndpoints()

                assertTrue("service_name" in endpoints)
                assertTrue("endpoints" in endpoints)
            }
        }
        null
    }

    suspend fun useWebWorkerService(exec: suspend (WebWorkerTestService) -> Unit) {
        val env = ksrpcEnvironment { }
        createServiceWorkerWithConnection(jsWorkerUrl(), env).use { connection ->
            connection.defaultChannel().toStub<WebWorkerTestService, String>().use { service ->
                exec(service)
            }
        }
    }
}

private fun jsWorkerUrl(): String = "base/kotlin/ksrpc-web-worker-test.js"

private fun hasWindow(): Boolean = js("typeof window !== 'undefined'") as Boolean
