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

package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.annotation.ExperimentalKsrpc
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.webworker.createServiceWorkerWithConnection
import com.monkopedia.ksrpc.webworker.test.WebWorkerTestService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ServiceWorkerTest {
    @Test
    fun wasmServiceWorkerConnection_methods() = runBlockingUnit {
        if (!hasWindow()) {
            return@runBlockingUnit skipUnsupported(
                "wasmServiceWorkerConnection_methods",
                "no window: service workers need a browsing context"
            )
        }

        useWebWorkerService { service ->
            assertEquals("pong:ping:js", service.ping("ping"))
            assertEquals("hello world", service.rpc("hello" to "world"))
        }

        null
    }

    @Test
    fun wasmServiceWorkerConnection_subservice() = runBlockingUnit {
        if (!hasWindow()) {
            return@runBlockingUnit skipUnsupported(
                "wasmServiceWorkerConnection_subservice",
                "no window: service workers need a browsing context"
            )
        }
        useWebWorkerService { service ->
            service.subservice("sub").use { sub ->
                assertEquals("sub a b", sub.rpc("a" to "b"))
            }
        }
        null
    }

    @Test
    fun wasmServiceWorkerConnection_introspection() = runBlockingUnit {
        if (!hasWindow()) {
            return@runBlockingUnit skipUnsupported(
                "wasmServiceWorkerConnection_introspection",
                "no window: service workers need a browsing context"
            )
        }
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
            return@runBlockingUnit skipUnsupported(
                "wasmServiceWorkerConnection_introspection_introspection",
                "no window: service workers need a browsing context"
            )
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

    @Test
    fun wasmServiceWorkerConnection_introspection_endpointInfo() = runBlockingUnit {
        if (!hasWindow()) {
            return@runBlockingUnit skipUnsupported(
                "wasmServiceWorkerConnection_introspection_endpointInfo",
                "no window: service workers need a browsing context"
            )
        }
        useWebWorkerService { service ->
            service.getIntrospection().use { introspection ->
                val rpcInfo = introspection.getEndpointInfo("rpc")
                assertEquals(
                    RpcDescriptor(
                        RpcDescriptorType.CLASS,
                        "kotlin.Pair",
                        mapOf(
                            "first" to
                                RpcDescriptor(RpcDescriptorType.STRING, "kotlin.String", id = 1),
                            "second" to
                                RpcDescriptor(RpcDescriptorType.STRING, "kotlin.String", id = 1)
                        )
                    ),
                    (rpcInfo.input as? RpcDataType.DataStructure)?.schema
                )
                assertEquals(
                    RpcDescriptor(RpcDescriptorType.STRING, "kotlin.String"),
                    (rpcInfo.output as? RpcDataType.DataStructure)?.schema
                )
                val serviceInfo = introspection.getEndpointInfo("service")
                assertEquals(
                    RpcDataType.Service(
                        "com.monkopedia.ksrpc.webworker.test.WebWorkerTestSubService"
                    ),
                    serviceInfo.output
                )
            }
        }
        null
    }

    @Test
    fun wasmServiceWorkerConnection_missingEndpoint() = runBlockingUnit {
        if (!hasWindow()) {
            return@runBlockingUnit skipUnsupported(
                "wasmServiceWorkerConnection_missingEndpoint",
                "no window: service workers need a browsing context"
            )
        }
        createServiceWorkerWithConnection(wasmWorkerUrl(), ksrpcEnvironment { }).use { connection ->
            val stub = connection.defaultChannel().toStub<EndpointNotFoundExtended, String>()
            val exception = assertFailsWith<RpcEndpointException> {
                stub.extra(Unit)
            }
            val message = exception.message ?: ""
            assertTrue(message.contains("Unknown endpoint: extra"), message)
        }
        null
    }

    @Test
    fun wasmServiceWorkerConnection_remoteDecodeFailureIsRpcException() = runBlockingUnit {
        if (!hasWindow()) {
            return@runBlockingUnit skipUnsupported(
                "wasmServiceWorkerConnection_remoteDecodeFailureIsRpcException",
                "no window: service workers need a browsing context"
            )
        }
        createServiceWorkerWithConnection(wasmWorkerUrl(), ksrpcEnvironment { }).use { connection ->
            val channel = connection.defaultChannel()
            val response = channel.call("ping", CallData.create("123"), callId = null)
            assertTrue(response.isError)

            val message = response.errorMessage ?: ""
            assertTrue(message.isNotBlank())
        }
        null
    }

    suspend fun useWebWorkerService(exec: suspend (WebWorkerTestService) -> Unit) {
        val env = ksrpcEnvironment { }
        createServiceWorkerWithConnection(wasmWorkerUrl(), env).use { connection ->
            connection.defaultChannel().toStub<WebWorkerTestService, String>().use { service ->
                exec(service)
            }
        }
    }
}

private fun wasmWorkerUrl(): String = "base/kotlin/ksrpc-service-worker-test.js"

/**
 * Whether this browser context has a `window`.
 *
 * Every test in this class drives a real service worker through [useWebWorkerService],
 * which needs a browsing context to register one. The karma launcher does not always
 * provide a `window`, so the tests guard on this and no-op when it is absent — which
 * `kotlin.test` reports as a pass. The guards route through `skipUnsupported` so the
 * no-op is named rather than silent; see issue #261.
 */
private val hasWindow: () -> Boolean =
    js("() => typeof window !== 'undefined'")
